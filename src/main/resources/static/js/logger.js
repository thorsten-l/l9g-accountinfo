const LEVELS = {
  debug: 10,
  info: 20,
  warn: 30,
  error: 40,
  off: 50
};

function readLevel() {
  const level = String(jsLogLevel ?? "warn").toLowerCase();
  return LEVELS[level] ?? LEVELS.warn;
}

let currentLevel = readLevel();

export function setLogLevel(level) {
  const l = String(level).toLowerCase();
  currentLevel = LEVELS[l] ?? currentLevel;
}

function ts() {
  return Temporal.Now.plainDateTimeISO()
    .toString({ smallestUnit: "second" })
    .replace(/T/g, " ");
}

export function createLogger(scope) {
  const scopePrefix = scope ? `[${scope}]` : "";

  const prefixArgs = (level) => [`${ts()} ${level}`, scopePrefix, "-"].filter(Boolean);

  return {
    debug: (...args) => currentLevel <= LEVELS.debug && console.debug(...prefixArgs("DEBUG"), ...args),
    info:  (...args) => currentLevel <= LEVELS.info  && console.info(...prefixArgs("INFO"), ...args),
    warn:  (...args) => currentLevel <= LEVELS.warn  && console.warn(...prefixArgs("WARN"), ...args),
    error: (...args) => currentLevel <= LEVELS.error && console.error(...prefixArgs("ERROR"), ...args),

    groupDebug: (label, fn) => {
      if (currentLevel <= LEVELS.debug) {
        const fullLabel = `${ts()} DEBUG ${scopePrefix} - ${label}`.trim().replace(/\s+/g, " ");
        console.groupCollapsed(fullLabel);
        try { fn(); } finally { console.groupEnd(); }
      }
    }
  };
}
