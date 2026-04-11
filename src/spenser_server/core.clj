(ns spenser-server.core
  (:gen-class)
  (:require
   [org.httpkit.server :as http]
   [clojure.string :as string])
  (:import
   [java.io FileWriter BufferedWriter File]
   [java.nio.file Files Paths StandardCopyOption]
   [java.time Instant]
   [java.util.concurrent.locks ReentrantLock]))

(def data-file "data/measurements.dat")

(defonce file-lock (ReentrantLock.))

(defn valid-entry? [[ts v]]
  (and (re-matches #"\d+" (string/trim ts))
       (re-matches #"\d+" (string/trim v))))

(defn parse-body [body]
  (->> (string/split (string/trim body) #";")
       (map #(string/split % #"," 2))
       (filter #(= 2 (count %)))
       (filter valid-entry?)
       (map (fn [[ts v]]
              [(parse-long (string/trim ts))
               (parse-long (string/trim v))]))))

(defn append-to-file! [entries]
  (.lock file-lock)
  (try
    (with-open [w (BufferedWriter. (FileWriter. data-file true))]
      (doseq [[ts v] entries]
        (.write w (str ts "," v "\n"))))
    (finally
      (.unlock file-lock))))

(defn archive-and-clear! []
  (.lock file-lock)
  (try
    (let [archive-file (str "data/measurements-" (Instant/now) ".dat")]
      (Files/copy (Paths/get data-file (make-array String 0))
                  (Paths/get archive-file (make-array String 0))
                  (into-array StandardCopyOption [StandardCopyOption/REPLACE_EXISTING]))
      (with-open [_ (FileWriter. data-file false)])
      archive-file)
    (finally
      (.unlock file-lock))))

(defn handler [req]
  (if (= (:request-method req) :delete)
    (if (.exists (File. data-file))
      (let [archive-file (archive-and-clear!)]
        (println "Archived and cleared")
        {:status 200
         :headers {"Content-Type" "text/plain"}
         :body (str "Archived to " archive-file "\n")})
      {:status 404
       :headers {"Content-Type" "text/plain"}
       :body "No measurements file found.\n"})
    (if (= (:request-method req) :post)
      (let [body (some-> (:body req) slurp)]
        (if (seq body)
          (let [entries (parse-body body)]
            (if (seq entries)
              (do
                (append-to-file! entries)
                (println "Received")
                {:status 200
                 :headers {"Content-Type" "text/plain"}})
              {:status 400
               :headers {"Content-Type" "text/plain"}
               :body "No valid entries found. Expected format: timestamp,value;timestamp,value\n"}))
          {:status 400
           :headers {"Content-Type" "text/plain"}
           :body "Empty body.\n"}))
      {:status 405
       :headers {"Content-Type" "text/plain"}
       :body "Method not allowed. Use POST or DELETE.\n"})))

(defn -main [& args]
  (.mkdirs (File. "data"))
  (let [port (or (some-> (first args) Integer/parseInt) 3000)]
    (println (str "Starting server on port " port))
    (println (str "Appending data to: " data-file))
    (http/run-server handler {:port port})))
