// table formatting for the list command
// column widths are fixed by the spec so both implementations produce
// identical stdout; changing them here breaks the acceptance test
const WIDTHS = { id: 6, status: 10, category: 12, assignee: 12 };

function pad(value, width) {
  const s = String(value);
  return s.length >= width ? s : s + ' '.repeat(width - s.length);
}

function header() {
  return (
    pad('ID', WIDTHS.id) +
    pad('STATUS', WIDTHS.status) +
    pad('CATEGORY', WIDTHS.category) +
    pad('ASSIGNEE', WIDTHS.assignee) +
    'TITLE'
  );
}

// row renders one task; an unassigned task shows a hyphen
function row(task, users) {
  let assignee = '-';
  if (task.assignee) {
    const user = users.find((u) => u.id === task.assignee);
    assignee = user ? user.name : task.assignee;
  }
  return (
    pad(task.id, WIDTHS.id) +
    pad(task.status, WIDTHS.status) +
    pad(task.category, WIDTHS.category) +
    pad(assignee, WIDTHS.assignee) +
    task.title
  );
}

// table sorts by createdAt then id, as the spec requires
function table(tasks, users) {
  if (tasks.length === 0) return 'no tasks match';
  const sorted = [...tasks].sort((a, b) => {
    if (a.createdAt !== b.createdAt) return a.createdAt < b.createdAt ? -1 : 1;
    return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
  });
  return [header(), ...sorted.map((t) => row(t, users))].join('\n');
}

function userList(users) {
  return users.map((u) => pad(u.id, WIDTHS.id) + u.name).join('\n');
}

module.exports = { WIDTHS, pad, header, row, table, userList };
