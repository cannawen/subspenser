const rawTimestamps = %s;
const allValues = %s;
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
    annotations['m' + i] = {
      type: 'line',
      xMin: d.getTime(),
      xMax: d.getTime(),
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
    datasets: [{
      label: 'Value',
      data: rawTimestamps.map(function(ts, i) { return { x: ts, y: allValues[i] }; }),
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
        type: 'time',
        time: {
          unit: 'hour',
          displayFormats: { hour: 'HH:mm' }
        },
        ticks: { maxRotation: 30 }
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
  chart.data.datasets[0].data = indices.map(function(i) { return { x: rawTimestamps[i], y: allValues[i] }; });
  chart.options.plugins.annotation.annotations = buildMidnightAnnotations(filteredTs);
  chart.update();
});
