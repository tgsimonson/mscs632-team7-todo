// command handlers
// each verb is a first-class function in a dispatch table rather than a
// switch or a class hierarchy, which is the idiomatic javascript shape and
// the clearest contrast with the java implementation's Command interface
const store = require('./store');
const format = require('./format');

class UsageError extends Error {
  constructor(message) {
    super(message);
    this.code = 1;
  }
}

const VALID_STATUS = ['pending', 'complete'];

function requireOption(opts, name) {
  const value = opts[name];
  if (value === undefined || value === '') {
    throw new UsageError(`missing required option --${name}`);
  }
  return value;
}

// timestamp returns iso 8601 utc at second precision, as the spec requires
function timestamp() {
  return `${new Date().toISOString().split('.')[0]}Z`;
}

async function add(opts) {
  const title = requireOption(opts, 'title');
  const category = requireOption(opts, 'category');

  if (title.length > 120) {
    throw new UsageError('title exceeds 120 characters');
  }

  const id = await store.withStore(async (s) => {
    if (opts.assignee) store.findUser(s, opts.assignee);
    const newId = store.nextTaskId(s.tasks);
    s.tasks.push({
      id: newId,
      title,
      category: category.toLowerCase(),
      status: 'pending',
      assignee: opts.assignee || null,
      createdAt: timestamp(),
    });
    return newId;
  });

  return `added ${id}`;
}

async function list(opts) {
  if (opts.status && !VALID_STATUS.includes(opts.status)) {
    throw new UsageError(`--status must be pending or complete`);
  }

  const s = await store.readStore();
  if (opts.user) store.findUser(s, opts.user);

  // filters combine with and
  const matched = s.tasks.filter((t) => {
    if (opts.user && t.assignee !== opts.user) return false;
    if (opts.category && t.category !== opts.category) return false;
    if (opts.status && t.status !== opts.status) return false;
    return true;
  });

  return format.table(matched, s.users);
}

async function assign(opts) {
  const id = requireOption(opts, 'id');
  const userId = requireOption(opts, 'user');

  const name = await store.withStore(async (s) => {
    const task = store.findTask(s, id);
    const user = store.findUser(s, userId);
    task.assignee = user.id;
    return user.name;
  });

  return `assigned ${id} to ${name}`;
}

async function complete(opts) {
  const id = requireOption(opts, 'id');

  await store.withStore(async (s) => {
    const task = store.findTask(s, id);
    // completing an already complete task is not an error
    task.status = 'complete';
  });

  return `completed ${id}`;
}

async function remove(opts) {
  const id = requireOption(opts, 'id');

  await store.withStore(async (s) => {
    store.findTask(s, id);
    s.tasks = s.tasks.filter((t) => t.id !== id);
  });

  return `removed ${id}`;
}

async function users() {
  const s = await store.readStore();
  return format.userList(s.users);
}

module.exports = { UsageError, add, list, assign, complete, remove, users };
