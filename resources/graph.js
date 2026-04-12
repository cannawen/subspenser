const rawTimestamps = %s;
const allValues = %s;
const ctx = document.getElementById('chart').getContext('2d');

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

const today = new Date();
today.setHours(0, 0, 0, 0);
const todayKey = String(today.getTime());
if (dayMap[todayKey]) {
  select.value = todayKey;
  select.dispatchEvent(new Event('change'));
}

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
      legend: { display: false }
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
  chart.data.datasets[0].data = indices.map(function(i) { return { x: rawTimestamps[i], y: allValues[i] }; });
  chart.update();
});
