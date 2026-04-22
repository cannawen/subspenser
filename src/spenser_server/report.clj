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
  (when (.exists (io/file data-file))
    (->> (slurp data-file)
         string/split-lines
         (keep parse-line)
         (sort-by first))))

(defn render-html [dates]
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
      [:script {:src "https://cdn.jsdelivr.net/npm/chartjs-adapter-date-fns"}]
      [:script {:src "https://cdn.jsdelivr.net/npm/chartjs-plugin-annotation"}]
      [:style "
              * { box-sizing: border-box; margin: 0; padding: 0; }
              html, body { width: 100%; height: 100%; }
              body { font-family: sans-serif; background: #f5f5f5; display: flex; flex-direction: column; }
              .toolbar { padding: 0.5rem 1rem; background: white; border-bottom: 1px solid #e5e7eb; display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; }
              .toolbar label { font-size: 0.875rem; color: #555; }
              .toolbar select, .toolbar input[type=number] { font-size: 0.875rem; padding: 0.25rem 0.5rem; border: 1px solid #d1d5db; border-radius: 4px; }
              .toolbar input[type=number] { width: 8rem; }
              .toolbar .sep { width: 1px; height: 1.5rem; background: #e5e7eb; }
              .chart-container { flex: 1; background: white; padding: 1.5rem; position: relative; }"]]
     [:body
      [:div.toolbar
       [:label {:for "day-pick"} "Day"]
       (into [:select#day-pick]
             (map (fn [d] [:option {:value d} d]) dates))
       [:div.sep]
       [:label {:for "y-min"} "Y min"]
       [:input#y-min {:type "number" :step "1000"}]
       [:label {:for "y-max"} "Y max"]
       [:input#y-max {:type "number" :step "1000"}]
       [:div.sep]
       [:label {:for "offset"} "Zero (raw)"]
       [:input#offset {:type "number" :step "1"}]
       [:label {:for "factor"} "Raw/kg"]
       [:input#factor {:type "number" :step "0.001"}]]
      [:div.chart-container
       [:canvas#chart]]
      [:script [:hiccup/raw-html (slurp (io/resource "graph.js"))]]]])))
