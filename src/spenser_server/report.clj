(ns spenser-server.report
  (:require
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
        chart-js (str "
    const rawTimestamps = " timestamps ";
    const labels = rawTimestamps.map(ts => new Date(ts).toLocaleString());
    const ctx = document.getElementById('chart').getContext('2d');
    new Chart(ctx, {
      type: 'line',
      data: {
        labels: labels,
        datasets: [{
          label: 'Value',
          data: " values ",
          borderColor: 'rgb(59, 130, 246)',
          backgroundColor: 'rgba(59, 130, 246, 0.7)',
          borderWidth: 0,
          pointRadius: 3,
          showLine: false
        }]
      },
      options: {
        responsive: true,
        plugins: {
          legend: { display: false }
        },
        scales: {
          x: {
            ticks: { maxTicksLimit: 12, maxRotation: 30 }
          },
          y: {
            min: 500000,
            max: 800000
          },
          y2: {
            position: 'right',
            grid: { drawOnChartArea: false },
            afterBuildTicks: function(scale) {
              const yScale = scale.chart.scales.y;
              if (yScale) {
                scale.min = yScale.min;
                scale.max = yScale.max;
                scale.ticks = yScale.ticks.map(t => ({ value: t.value }));
              }
            },
            ticks: {
              callback: function(value) {
                return ((value - 540860) / 24.267).toFixed(1);
              }
            }
          }
        }
      }
    });")]
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
        [:style "
                body { font-family: sans-serif; margin: 2rem; background: #f5f5f5; }
                      h1   { color: #333; }
                            .chart-container { background: white; border-radius: 8px; padding: 1.5rem;
                                              box-shadow: 0 2px 8px rgba(0,0,0,0.1); max-width: 1200px; }"]]
       [:body
        [:h1 "Measurements Over Time"]
        [:p (count data) " data points"]
        [:div.chart-container
         [:canvas#chart]]
        [:script [:hiccup/raw-html chart-js]]]]))))
