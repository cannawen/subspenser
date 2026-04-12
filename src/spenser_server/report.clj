(ns spenser-server.report
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [huff2.core :as h]))

(defn parse-line [line]
  (let [[ts v] (string/split (string/trim line) #",")]
    (when (and ts v)
      [(parse-long ts) (parse-long v)])))

(defn load-data [data-file]
  (->> (slurp data-file)
       string/split-lines
       (keep parse-line)
       (sort-by first)))

(defn render-html [data]
  (let [timestamps (->> data
                        (map first)
                        (string/join ",")
                        (format "[%s]"))
        values (->> data
                    (map second)
                    (string/join ",")
                    (format "[%s]"))
        chart-js (format (slurp (io/resource "graph.js")) timestamps values)]
    (str
     (h/html
      {:doctype? true
       :allow-raw true}
      [:html {:lang "en"}
       [:head
        [:meta {:charset "UTF-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
        [:title "Measurements Report"]
        [:script {:src "https://cdn.jsdelivr.net/npm/chart.js"}]
        [:script {:src "https://cdn.jsdelivr.net/npm/chartjs-plugin-annotation"}]
        [:style "
                * { box-sizing: border-box; margin: 0; padding: 0; }
                html, body { width: 100%; height: 100%; }
                body { font-family: sans-serif; background: #f5f5f5; display: flex; flex-direction: column; }
                .toolbar { padding: 0.5rem 1rem; background: white; border-bottom: 1px solid #e5e7eb; display: flex; align-items: center; gap: 0.5rem; }
                .toolbar label { font-size: 0.875rem; color: #555; }
                .toolbar select { font-size: 0.875rem; padding: 0.25rem 0.5rem; border: 1px solid #d1d5db; border-radius: 4px; }
                .chart-container { flex: 1; background: white; padding: 1.5rem; position: relative; }"]]
       [:body
        [:div.toolbar
         [:label {:for "day-select"} "Day"]
         [:select#day-select]]
        [:div.chart-container
         [:canvas#chart]]
        [:script [:hiccup/raw-html chart-js]]]]))))
