export const motion = {
  duration: {
    instant: '0ms',
    fast: '120ms',
    normal: '220ms',
    slow: '360ms',
  },
  easing: {
    standard: 'cubic-bezier(0.2, 0, 0, 1)',
    emphasized: 'cubic-bezier(0.2, 0, 0, 1.2)',
    exit: 'cubic-bezier(0.4, 0, 1, 1)',
  },
} as const;
