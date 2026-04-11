(ns spenser-server.format
  (:require
   [clojure.string :as string]))

(defn- valid-entry? [[ts v]]
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
