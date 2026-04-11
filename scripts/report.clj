#!/usr/bin/env bb

(require '[clojure.string :as str])

(def data-file "data/measurements.dat")
(def output-file "data/report.html")

(defn parse-line [line]
  (let [[ts v] (str/split (str/trim line) #",")]
    (when (and ts v)
      [(parse-long ts) (parse-long v)])))

(defn load-data []
  (->> (slurp data-file)
       str/split-lines
       (keep parse-line)
       (sort-by first)))

(defn format-timestamp [ms]
  (let [inst (java.time.Instant/ofEpochMilli ms)
        zdt  (java.time.ZonedDateTime/ofInstant inst (java.time.ZoneId/systemDefault))
        fmt  (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")]
    (.format zdt fmt)))

(defn render-html [data]
  (let [labels (str "[" (str/join "," (map #(str "\"" (format-timestamp (first %)) "\"") data)) "]")
        values (str "[" (str/join "," (map second data)) "]")]
    (str "<!DOCTYPE html>
<html lang=\"en\">
<head>
  <meta charset=\"UTF-8\">
  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
  <title>Measurements Report</title>
  <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>
  <style>
    body { font-family: sans-serif; margin: 2rem; background: #f5f5f5; }
    h1   { color: #333; }
    .chart-container { background: white; border-radius: 8px; padding: 1.5rem;
                       box-shadow: 0 2px 8px rgba(0,0,0,0.1); max-width: 1200px; }
  </style>
</head>
<body>
  <h1>Measurements Over Time</h1>
  <p>" (count data) " data points</p>
  <div class=\"chart-container\">
    <canvas id=\"chart\"></canvas>
  </div>
  <script>
    const ctx = document.getElementById('chart').getContext('2d');
    new Chart(ctx, {
      type: 'line',
      data: {
        labels: " labels ",
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
            beginAtZero: false
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
    });
  </script>
</body>
</html>")))

(let [data (load-data)]
  (if (empty? data)
    (do (println "No data found in" data-file) (System/exit 1))
    (do
      (spit output-file (render-html data))
      (println (str "Report written to " output-file " (" (count data) " points)")))))
