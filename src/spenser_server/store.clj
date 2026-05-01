(ns spenser-server.store
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string])
  (:import
   [java.io BufferedWriter File FileWriter]
   [java.nio.file Files StandardCopyOption]
   [java.time Instant LocalDate ZoneId]
   [java.time.format DateTimeFormatter]
   [java.util.concurrent.locks ReentrantLock]))

(def data-dir "data")
(def legacy-data-file (str data-dir "/measurements.dat"))

(def ^:private date-fmt (DateTimeFormatter/ofPattern "yyyy-MM-dd"))
(def ^:private system-zone (ZoneId/systemDefault))

(defonce file-lock (ReentrantLock.))

(defn today-str []
  (.format (LocalDate/now system-zone) date-fmt))

(defn- date-str-from-ts [ts-ms]
  (-> (Instant/ofEpochMilli ts-ms)
      (.atZone system-zone)
      .toLocalDate
      (.format date-fmt)))

(defn data-file-for-date [date-str]
  (str data-dir "/" date-str "-measurements.dat"))

(defn list-data-files []
  (let [files (.listFiles (File. data-dir))]
    (->> (or files [])
         (filter (fn [f] (re-matches #"\d{4}-\d{2}-\d{2}-measurements\.dat" (.getName f))))
         (sort-by #(.getName %)))))

(defn list-dates []
  (->> (list-data-files)
       (map (fn [f] (-> (.getName f)
                        (string/replace #"-measurements\.dat$" ""))))
       reverse))

(defn parse-line [line]
  (let [[ts v] (string/split (string/trim line) #",")]
    (when (and ts v)
      [(parse-long ts) (parse-long v)])))

(defn load-data [data-file]
  (when (.exists (io/file data-file))
    (->> (slurp data-file)
         string/split-lines
         (keep parse-line)
         (sort-by first))))

(defn- start-of-day-ms [^LocalDate date ^ZoneId tz-id]
  (-> date (.atStartOfDay tz-id) .toInstant .toEpochMilli))

(defn- server-dates-for-range [start-ms end-ms]
  (let [start-date (-> (Instant/ofEpochMilli start-ms) (.atZone system-zone) .toLocalDate)
        end-date   (-> (Instant/ofEpochMilli (dec end-ms)) (.atZone system-zone) .toLocalDate)]
    (loop [d start-date result []]
      (if (.isAfter d end-date)
        result
        (recur (.plusDays d 1) (conj result (.format d date-fmt)))))))

(defn list-dates-for-tz [tz-str]
  (let [tz-id (ZoneId/of tz-str)]
    (->> (list-dates)
         (mapcat (fn [server-date]
                   (let [parsed    (LocalDate/parse server-date date-fmt)
                         start-ms  (start-of-day-ms parsed system-zone)
                         end-ms    (start-of-day-ms (.plusDays parsed 1) system-zone)
                         user-start (.format (-> (Instant/ofEpochMilli start-ms) (.atZone tz-id) .toLocalDate) date-fmt)
                         user-end   (.format (-> (Instant/ofEpochMilli (dec end-ms)) (.atZone tz-id) .toLocalDate) date-fmt)]
                     (if (= user-start user-end)
                       [user-start]
                       [user-start user-end]))))
         distinct
         sort
         reverse)))

(defn load-data-for-user-day [date-str tz-str]
  (let [tz-id    (ZoneId/of tz-str)
        parsed   (LocalDate/parse date-str date-fmt)
        start-ms (start-of-day-ms parsed tz-id)
        end-ms   (start-of-day-ms (.plusDays parsed 1) tz-id)]
    (->> (server-dates-for-range start-ms end-ms)
         (mapcat (fn [sd] (load-data (data-file-for-date sd))))
         (filter (fn [[ts _]] (and (>= ts start-ms) (< ts end-ms))))
         (sort-by first))))

(defn exists? []
  (boolean (seq (list-data-files))))

(defn append! [entries]
  (.lock file-lock)
  (try
    (doseq [[date-str date-entries] (group-by (fn [[ts _]] (date-str-from-ts ts)) entries)]
      (with-open [w (BufferedWriter. (FileWriter. (data-file-for-date date-str) true))]
        (doseq [[ts v] date-entries]
          (.write w (str ts "," v "\n")))))
    (finally
      (.unlock file-lock))))

(defn archive-and-clear! []
  (.lock file-lock)
  (try
    (let [archive-dir (File. (str data-dir "/archive"))
          _ (.mkdirs archive-dir)
          ts-str (str (Instant/now))
          files (list-data-files)]
      (doseq [f files]
        (Files/move
         (.toPath f)
         (.toPath (File. archive-dir (str ts-str "-" (.getName f))))
         (into-array StandardCopyOption [StandardCopyOption/REPLACE_EXISTING])))
      (str "Archived " (count files) " file(s) to " (.getPath archive-dir)))
    (finally
      (.unlock file-lock))))

(defn migrate-legacy! []
  (let [legacy (File. legacy-data-file)]
    (when (.exists legacy)
      (println "Migrating legacy measurements.dat to per-date files...")
      (.lock file-lock)
      (try
        (let [entries (->> (slurp legacy)
                           string/split-lines
                           (keep (fn [line]
                                   (let [parts (string/split (string/trim line) #",")]
                                     (when (= 2 (count parts))
                                       (let [ts (parse-long (first parts))
                                             v  (parse-long (second parts))]
                                         (when (and ts v)
                                           [ts v])))))))]
          (doseq [[date-str date-entries] (group-by (fn [[ts _]] (date-str-from-ts ts)) entries)]
            (with-open [w (BufferedWriter. (FileWriter. (data-file-for-date date-str) true))]
              (doseq [[ts v] date-entries]
                (.write w (str ts "," v "\n")))))
          (Files/move
           (.toPath legacy)
           (.toPath (File. (str legacy-data-file ".migrated")))
           (into-array StandardCopyOption [StandardCopyOption/REPLACE_EXISTING]))
          (println (str "Migration complete. " (count entries) " entries redistributed.")))
        (finally
          (.unlock file-lock))))))
