(ns spenser-server.core
  (:gen-class)
  (:require
   [clojure.string :as string]
   [org.httpkit.server :as http]
   [reitit.ring :as rring]
   [ring.middleware.params :as params]
   [spenser-server.format :as format]
   [spenser-server.report :as report]
   [spenser-server.store :as store])
  (:import
   [java.io File]))

(defn get-raw [req]
  (let [date-str (get-in req [:query-params "date"])
        files (if date-str
                (let [f (store/data-file-for-date date-str)]
                  (when (.exists (File. f)) [f]))
                (map #(.getPath %) (store/list-data-files)))]
    (if (seq files)
      {:status  200
       :headers {"Content-Type" "text/plain"}
       :body    (string/join "" (map slurp files))}
      {:status  404
       :headers {"Content-Type" "text/plain"}
       :body    "No measurements files found.\n"})))

(defn get-index [_req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (report/render-html)})

(defn get-dates [req]
  (let [tz-str (get-in req [:query-params "tz"])
        dates  (if tz-str
                 (store/list-dates-for-tz tz-str)
                 (store/list-dates))
        body   (str "[" (string/join "," (map (fn [d] (str "\"" d "\"")) dates)) "]")]
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    body}))

(defn get-data [req]
  (let [date-str (or (get-in req [:query-params "date"]) (store/today-str))
        tz-str   (get-in req [:query-params "tz"])
        data     (if tz-str
                   (store/load-data-for-user-day date-str tz-str)
                   (report/load-data (store/data-file-for-date date-str)))
        timestamps (string/join "," (map first data))
        values     (string/join "," (map second data))]
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    (str "{\"timestamps\":[" timestamps "],\"values\":[" values "]}")}))

(defn post-index [req]
  (let [body (some-> (:body req) slurp)]
    (if (seq body)
      (let [entries (format/parse-body body)]
        (if (seq entries)
          (do
            (store/append! entries)
            (println "Received")
            {:status  200
             :headers {"Content-Type" "text/plain"}})
          {:status  400
           :headers {"Content-Type" "text/plain"}
           :body    "No valid entries found. Expected format: timestamp,value;timestamp,value\n"}))
      {:status  400
       :headers {"Content-Type" "text/plain"}
       :body    "Empty body.\n"})))

(defn delete-index [_req]
  (if (store/exists?)
    (let [result (store/archive-and-clear!)]
      (println "Archived and cleared")
      {:status  200
       :headers {"Content-Type" "text/plain"}
       :body    (str result "\n")})
    {:status  404
     :headers {"Content-Type" "text/plain"}
     :body    "No measurements files found.\n"}))

(def app
  (-> (rring/ring-handler
       (rring/router
        [["/" {:get    get-index
               :post   post-index
               :delete delete-index}]
         ["/dates" {:get get-dates}]
         ["/data" {:get get-data}]
         ["/raw" {:get get-raw}]])
       (rring/create-default-handler))
      params/wrap-params))

(defn -main [& args]
  (.mkdirs (File. store/data-dir))
  (store/migrate-legacy!)
  (let [port (or (some-> (first args) Integer/parseInt) 3000)]
    (println (str "Starting server on port " port))
    (http/run-server #'app {:port port})))
