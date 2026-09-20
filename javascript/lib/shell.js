// interactive session
// the one-shot commands remain the primary interface and are what the
// acceptance test drives. this wraps the same dispatch table in a readline
// loop so a single session can act as one user over many commands, which is
// what a live demonstration of multiple users needs
const readline = require('readline');

// tokenize splits a line on whitespace while keeping quoted strings together
function tokenize(line) {
  const tokens = [];
  let current = '';
  let quote = null;

  for (const ch of line) {
    if (quote) {
      if (ch === quote) {
        quote = null;
      } else {
        current += ch;
      }
    } else if (ch === '"' || ch === "'") {
      quote = ch;
    } else if (/\s/.test(ch)) {
      if (current) {
        tokens.push(current);
        current = '';
      }
    } else {
      current += ch;
    }
  }
  if (current) tokens.push(current);
  return tokens;
}

const HELP = [
  'commands:',
  '  add --title "<text>" --category <cat> [--assignee <userId>]',
  '  list [--user <id>] [--category <cat>] [--status pending|complete]',
  '  assign --id <taskId> --user <userId>',
  '  complete --id <taskId>',
  '  remove --id <taskId>',
  '  adduser --name <name>',
  '  users',
  '  concurrency-test --workers <n> --ops <n> [--no-lock]',
  '  help',
  '  exit',
].join('\n');

async function start({ table, parseOptions, user }) {
  const prompt = user ? `todo(${user})> ` : 'todo> ';
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    prompt,
  });

  console.log('collaborative to-do list, interactive session');
  console.log('type help for commands, exit to quit\n');
  rl.prompt();

  // for await serializes the loop: the next line is not read until the
  // current command finishes. the event handler form races on piped input,
  // because readline emits every line before the first await resolves
  for await (const line of rl) {
    const tokens = tokenize(line.trim());

    if (tokens.length === 0) {
      rl.prompt();
      continue;
    }

    const [verb, ...rest] = tokens;

    if (verb === 'exit' || verb === 'quit') break;

    if (verb === 'help') {
      console.log(HELP);
      rl.prompt();
      continue;
    }

    const handler = table[verb];
    if (!handler) {
      console.log(`error: unknown command ${verb}`);
      rl.prompt();
      continue;
    }

    try {
      const output = await handler(parseOptions(rest));
      if (output) console.log(output);
    } catch (err) {
      console.log(`error: ${err.message}`);
    }
    rl.prompt();
  }

  rl.close();
  console.log('session ended');
}

module.exports = { start, tokenize, HELP };
