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
    const allValues = " values ";
    const ctx = document.getElementById('chart').getContext('2d');

    function buildMidnightAnnotations(timestamps) {
      if (timestamps.length === 0) return {};
      const minTs = Math.min(...timestamps);
      const maxTs = Math.max(...timestamps);
      const annotations = {};
      const d = new Date(minTs);
      d.setHours(0, 0, 0, 0);
      d.setDate(d.getDate() + 1);
      let i = 0;
      while (d.getTime() <= maxTs) {
        let closest = 0, minDiff = Infinity;
        timestamps.forEach(function(ts, idx) {
          const diff = Math.abs(ts - d.getTime());
          if (diff < minDiff) { minDiff = diff; closest = idx; }
        });
        annotations['m' + i] = {
          type: 'line',
          xMin: closest,
          xMax: closest,
          borderColor: 'rgba(0, 0, 0, 0.25)',
          borderWidth: 1,
          borderDash: [4, 4]
        };
        d.setDate(d.getDate() + 1);
        i++;
      }
      return annotations;
    }

    const dayMap = {};
    rawTimestamps.forEach(function(ts, i) {
      const date = new Date(ts);
      date.setHours(0, 0, 0, 0);
      const key = date.getTime();
      if (!dayMap[key]) dayMap[key] = { label: date.toLocaleDateString(), indices: [] };
      dayMap[key].indices.push(i);
    });

    const select = document.getElementById('day-select');
    Object.keys(dayMap).sort().forEach(function(key) {
      const opt = document.createElement('option');
      opt.value = key;
      opt.textContent = dayMap[key].label;
      select.appendChild(opt);
    });

    const chart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: rawTimestamps.map(function(ts) { return new Date(ts).toLocaleString(); }),
        datasets: [{
          label: 'Value',
          data: allValues,
          borderColor: 'rgb(59, 130, 246)',
          backgroundColor: 'rgba(59, 130, 246, 0.7)',
          borderWidth: 0,
          pointRadius: 3,
          showLine: false
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          annotation: { annotations: buildMidnightAnnotations(rawTimestamps) }
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
    });

    select.addEventListener('change', function() {
      const indices = dayMap[this.value].indices;
      const filteredTs = indices.map(function(i) { return rawTimestamps[i]; });
      chart.data.labels = filteredTs.map(function(ts) { return new Date(ts).toLocaleString(); });
      chart.data.datasets[0].data = indices.map(function(i) { return allValues[i]; });
      chart.options.plugins.annotation.annotations = buildMidnightAnnotations(filteredTs);
      chart.update();
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
