#!/usr/bin/env node

/**
 * Discord Test Server Setup Script
 * 
 * Creates test channels and threads for testing the QOTD channel tree feature.
 * 
 * Usage:
 *   node scripts/setup-test-server.js <guild-id> [--cleanup] [--token <bot-token>]
 * 
 * Example:
 *   node scripts/setup-test-server.js 123456789 --token your_bot_token
 *   node scripts/setup-test-server.js 123456789 --cleanup
 * 
 * Environment:
 *   Reads DISCORD_TOKEN from .env if not provided via --token
 */

require('dotenv').config();
const { Client, ChannelType, PermissionFlagsBits } = require('discord.js');
const fs = require('fs');
const path = require('path');

// Parse command-line arguments
const args = process.argv.slice(2);
const guildId = args[0];
const cleanup = args.includes('--cleanup');
const tokenIndex = args.indexOf('--token');
const token = tokenIndex !== -1 ? args[tokenIndex + 1] : process.env.DISCORD_TOKEN;

if (!guildId) {
  console.error('Usage: node scripts/setup-test-server.js <guild-id> [--cleanup] [--token <bot-token>]');
  console.error('Example: node scripts/setup-test-server.js 123456789');
  process.exit(1);
}

if (!token) {
  console.error('Error: DISCORD_TOKEN not found in .env or --token argument');
  process.exit(1);
}

const client = new Client({ intents: ['Guilds', 'GuildMessages'] });

// Config for test channels and threads
const TEST_CHANNELS = [
  {
    name: 'test-no-threads',
    description: 'Test channel without any threads',
    threads: []
  },
  {
    name: 'test-with-2-threads',
    description: 'Test channel with 2 threads',
    threads: [
      { name: 'thread-1-discussion', message: 'Test thread 1' },
      { name: 'thread-2-questions', message: 'Test thread 2' }
    ]
  },
  {
    name: 'test-with-3-threads',
    description: 'Test channel with 3 threads',
    threads: [
      { name: 'announcements-thread', message: 'Announcements go here' },
      { name: 'bugs-thread', message: 'Bug reports' },
      { name: 'feature-requests-thread', message: 'Feature requests' }
    ]
  },
  {
    name: 'test-empty-channel',
    description: 'Empty test channel',
    threads: []
  }
];

// Output file for test config
const outputFile = path.join(__dirname, '..', 'e2e', 'test-server-config.json');

client.on('ready', async () => {
  console.log(`\n✓ Connected as ${client.user.username}\n`);

  try {
    const guild = await client.guilds.fetch(guildId);
    console.log(`✓ Found guild: ${guild.name}`);

    if (cleanup) {
      await cleanupTestChannels(guild);
    } else {
      await createTestChannels(guild);
    }

    console.log('\n✓ Done!\n');
    process.exit(0);
  } catch (error) {
    console.error('Error:', error.message);
    process.exit(1);
  }
});

async function createTestChannels(guild) {
  console.log(`Creating test channels in ${guild.name}...\n`);

  const testConfig = {
    guildId: guild.id,
    guildName: guild.name,
    timestamp: new Date().toISOString(),
    channels: []
  };

  for (const channelConfig of TEST_CHANNELS) {
    try {
      // Create channel
      const channel = await guild.channels.create({
        name: channelConfig.name,
        type: ChannelType.GuildText,
        topic: channelConfig.description
      });

      console.log(`  ✓ Created channel: #${channel.name} (${channel.id})`);

      const channelInfo = {
        id: channel.id,
        name: channel.name,
        threads: []
      };

      // Create threads in this channel
      for (const threadConfig of channelConfig.threads) {
        try {
          // Send a message to create the thread
          const message = await channel.send(threadConfig.message);
          
          // Create thread from message
          const thread = await message.startThread({
            name: threadConfig.name,
            autoArchiveDuration: 60 // 1 hour
          });

          console.log(`    ✓ Created thread: ${thread.name} (${thread.id})`);

          channelInfo.threads.push({
            id: thread.id,
            name: thread.name,
            parentId: channel.id
          });
        } catch (error) {
          console.error(`    ✗ Failed to create thread ${threadConfig.name}: ${error.message}`);
        }
      }

      testConfig.channels.push(channelInfo);
    } catch (error) {
      console.error(`  ✗ Failed to create channel ${channelConfig.name}: ${error.message}`);
    }
  }

  // Save config for E2E tests
  try {
    const dir = path.dirname(outputFile);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    fs.writeFileSync(outputFile, JSON.stringify(testConfig, null, 2));
    console.log(`\n✓ Test configuration saved to e2e/test-server-config.json`);
  } catch (error) {
    console.error(`✗ Failed to save config: ${error.message}`);
  }

  console.log('\nTest structure created:');
  testConfig.channels.forEach(ch => {
    console.log(`  📌 #${ch.name}`);
    if (ch.threads.length > 0) {
      ch.threads.forEach(th => {
        console.log(`    🧵 ${th.name}`);
      });
    }
  });
}

async function cleanupTestChannels(guild) {
  console.log(`Cleaning up test channels from ${guild.name}...\n`);

  const testChannelNames = TEST_CHANNELS.map(ch => ch.name);

  try {
    const channels = await guild.channels.fetch();
    let deleted = 0;

    for (const [, channel] of channels) {
      if (testChannelNames.includes(channel.name)) {
        await channel.delete();
        console.log(`  ✓ Deleted channel: #${channel.name}`);
        deleted++;
      }
    }

    if (deleted === 0) {
      console.log('  No test channels found to delete');
    }

    // Remove config file
    if (fs.existsSync(outputFile)) {
      fs.unlinkSync(outputFile);
      console.log('✓ Removed test configuration file');
    }
  } catch (error) {
    console.error(`Error during cleanup: ${error.message}`);
  }
}

client.login(token);
