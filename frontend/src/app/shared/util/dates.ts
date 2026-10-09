/** Edad cumplida en años a una fecha dada (ambas en formato ISO yyyy-MM-dd). */
export function ageOn(dateOfBirth: string, onDate: string): number {
  const [by, bm, bd] = dateOfBirth.split('-').map(Number);
  const [y, m, d] = onDate.split('-').map(Number);
  let age = y - by;
  if (m < bm || (m === bm && d < bd)) {
    age--;
  }
  return age;
}

/** Fecha local de hoy (+ días) en formato ISO, sin desfases de zona horaria. */
export function isoDate(daysFromToday = 0, today = new Date()): string {
  const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() + daysFromToday);
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${mm}-${dd}`;
}
