#!/usr/bin/env bb

(require '[babashka.curl :as curl])

(def port (or (first *command-line-args*) "3000"))
(def url (str "http://localhost:" port))

(defn make-body []
  (let [now (System/currentTimeMillis)]
    (->> (range 100)
         (map (fn [i] (str (+ now i) "," (rand-int 10000))))
         (clojure.string/join ";"))))

(println (str "Sending 100 data points/s to " url))
(println "Press Ctrl+C to stop.")

(loop []
  (try
    (let [body (make-body)
          res  (curl/post url {:body body})]
      (println (str "[" (System/currentTimeMillis) "] status=" (:status res))))
    (catch Exception e
      (println (str "Error: " (ex-message e)))))
  (Thread/sleep 1000)
  (recur))
