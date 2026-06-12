const durations = {
    instant: '0ms',
    fast: '100ms',
    normal: '200ms',
    slow: '300ms',
    slower: '500ms',
} as const;

const easings = {
    easeOut: 'cubic-bezier(0.16, 1, 0.3, 1)',
    easeIn: 'cubic-bezier(0.7, 0, 0.84, 0)',
    easeInOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
    spring: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
    standard: 'cubic-bezier(0.2, 0, 0, 1)',
    emphasized: 'cubic-bezier(0.2, 0, 0, 1.2)',
    exit: 'cubic-bezier(0.4, 0, 1, 1)',
} as const;

export const motion = {
  durations,
  duration: durations,
  easings,
  easing: easings,
  stagger: {
    base: '50ms',
    fast: '30ms',
    slow: '80ms',
  },
  keyframes: {
    fadeIn: 'fade-in',
    fadeOut: 'fade-out',
    slideUp: 'slide-up',
    slideDown: 'slide-down',
    slideLeft: 'slide-left',
    slideRight: 'slide-right',
    scaleIn: 'scale-in',
    scaleOut: 'scale-out',
    shimmer: 'shimmer',
    pulse: 'pulse',
  },
  transition: {
    glassSurface: 'background 200ms cubic-bezier(0.16, 1, 0.3, 1), border-color 200ms cubic-bezier(0.16, 1, 0.3, 1), box-shadow 200ms cubic-bezier(0.16, 1, 0.3, 1), transform 200ms cubic-bezier(0.16, 1, 0.3, 1)',
    railMode: 'grid-template-columns 200ms cubic-bezier(0.16, 1, 0.3, 1), width 200ms cubic-bezier(0.16, 1, 0.3, 1), transform 200ms cubic-bezier(0.16, 1, 0.3, 1)',
  },
} as const;

export const duration = durations;
export const easing = easings;
export type MotionKeyframe = keyof typeof motion.keyframes;
