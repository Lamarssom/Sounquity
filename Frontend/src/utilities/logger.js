// src/utilities/logger.js
const isDev = process.env.NODE_ENV === "development";

// Log levels: 0 (off), 1 (error), 2 (warn), 3 (info), 4 (debug)
const LOG_LEVEL = isDev ? 3 : 1; // Debug in development, errors only in production

const logger = {
  error: (prefix, ...args) => {
    if (LOG_LEVEL >= 1) console.error(`[${prefix}], ...args`);
  },
  warn: (prefix, ...args) => {
    if (LOG_LEVEL >= 2) console.warn(`[${prefix}], ...args`);
  },
  info: (prefix, ...args) => {
    if (LOG_LEVEL >= 3) console.log(`[${prefix}], ...args`);
  },
  debug: (prefix, ...args) => {
    if (LOG_LEVEL >= 4) console.log(`[${prefix}], ...args`);
  },
};

export default logger;