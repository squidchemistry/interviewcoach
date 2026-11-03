// jest-dom adds custom jest matchers for asserting on DOM nodes.
// allows you to do things like:
// expect(element).toHaveTextContent(/react/i)
// learn more: https://github.com/testing-library/jest-dom
import '@testing-library/jest-dom';

// React Router 7 uses TextEncoder, which CRA's Jest 27 jsdom environment lacks.
import { TextDecoder, TextEncoder } from 'util';

if (typeof global.TextEncoder === 'undefined') {
  Object.assign(global, { TextEncoder, TextDecoder });
}
