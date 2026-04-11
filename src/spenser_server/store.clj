(ns spenser-server.store
  (:import
   [java.io BufferedWriter File FileWriter]
   [java.nio.file Files Paths StandardCopyOption]
   [java.time Instant]
   [java.util.concurrent.locks ReentrantLock]))

(def data-file "data/measurements.dat")

(defonce file-lock (ReentrantLock.))

(defn append! [entries]
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

(defn exists? []
  (.exists (File. data-file)))
