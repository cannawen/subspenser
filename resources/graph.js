const rawTimestamps = %s;
const allValues = %s;
const ctx = document.getElementById('chart').getContext('2d');

const defaults = { yMin: 500000, yMax: 800000, offset: 540860, factor: 24.267 };

function loadSettings() {
  return {
    yMin:   parseFloat(localStorage.getItem('yMin')   ?? defaults.yMin),
    yMax:   parseFloat(localStorage.getItem('yMax')   ?? defaults.yMax),
    offset: parseFloat(localStorage.getItem('offset') ?? defaults.offset),
    factor: parseFloat(localStorage.getItem('factor') ?? defaults.factor)
  };
}

function saveSettings(s) {
  localStorage.setItem('yMin',   s.yMin);
  localStorage.setItem('yMax',   s.yMax);
  localStorage.setItem('offset', s.offset);
  localStorage.setItem('factor', s.factor);
}

function applySettings(s) {
  chart.options.scales.y.min = s.yMin;
  chart.options.scales.y.max = s.yMax;
  chart.options.scales.y2.min = s.yMin;
  chart.options.scales.y2.max = s.yMax;
  chart.options.scales.y2.ticks.callback = function(value) {
    return ((value - s.offset) / s.factor).toFixed(1) + ' kg';
  };
  chart.update();
}

const settings = loadSettings();

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
        min: settings.yMin,
        max: settings.yMax
      },
      y2: {
        position: 'right',
        grid: { drawOnChartArea: false },
        min: settings.yMin,
        max: settings.yMax,
        afterBuildTicks: function(scale) {
          const yScale = scale.chart.scales.y;
          if (yScale) {
            scale.min = yScale.min;
            scale.max = yScale.max;
            scale.ticks = yScale.ticks.map(function(t) { return { value: t.value }; });
          }
        },
        ticks: {
          callback: function(value) {
            return ((value - settings.offset) / settings.factor).toFixed(1) + ' kg';
          }
        }
      }
    }
  }
});

function wireInput(id, key, parse) {
  const el = document.getElementById(id);
  el.value = settings[key];
  el.addEventListener('change', function() {
    const s = loadSettings();
    s[key] = parse(this.value);
    saveSettings(s);
    applySettings(s);
  });
}

wireInput('y-min',   'yMin',   parseFloat);
wireInput('y-max',   'yMax',   parseFloat);
wireInput('offset',  'offset', parseFloat);
wireInput('factor',  'factor', parseFloat);
