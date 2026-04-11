(ns spenser-server.core
  (:gen-class)
  (:require
   [org.httpkit.server :as http]
   [spenser-server.format :as format]
   [spenser-server.report :as report]
   [spenser-server.store :as store])
  (:import
   [java.io File]))

(defn handler [req]
  (case (:request-method req)
    :get
    (if (store/exists?)
      (let [data (report/load-data store/data-file)]
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (report/render-html data)})
      {:status  404
       :headers {"Content-Type" "text/plain"}
       :body    "No measurements file found.\n"})

    :post
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
         :body    "Empty body.\n"}))

    :delete
    (if (store/exists?)
      (let [archive-file (store/archive-and-clear!)]
        (println "Archived and cleared")
        {:status  200
         :headers {"Content-Type" "text/plain"}
         :body    (str "Archived to " archive-file "\n")})
      {:status  404
       :headers {"Content-Type" "text/plain"}
       :body    "No measurements file found.\n"})

    {:status  405
     :headers {"Content-Type" "text/plain"}
     :body    "Method not allowed. Use GET, POST, or DELETE.\n"}))

(defn -main [& args]
  (.mkdirs (File. "data"))
  (let [port (or (some-> (first args) Integer/parseInt) 3000)]
    (println (str "Starting server on port " port))
    (println (str "Appending data to: " store/data-file))
    (http/run-server #'handler {:port port})))
