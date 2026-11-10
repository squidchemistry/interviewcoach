export type Field = 'name' | 'email' | 'password';

/** Mirrors the server's rules so most mistakes are caught before a round trip. */
export function validateRegistration(values: Record<Field, string>): Partial<Record<Field, string>> {
  const errors: Partial<Record<Field, string>> = {};
  if (!values.name.trim()) errors.name = 'Enter your name.';
  else if (values.name.trim().length > 80) errors.name = 'Use at most 80 characters.';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email.trim())) errors.email = 'Enter a valid email address.';
  if (values.password.length < 10 || values.password.length > 128) {
    errors.password = 'Use 10 to 128 characters.';
  } else if (!/\p{L}/u.test(values.password) || !/\d/.test(values.password)) {
    errors.password = 'Include at least one letter and one number.';
  }
  return errors;
}
