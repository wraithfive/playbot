#!/usr/bin/env node
/**
 * Generate production Privacy Policy and Terms of Service from templates
 * 
 * This script reads the .template.tsx files and replaces placeholders with
 * actual values from environment variables or defaults.
 * 
 * Usage: node scripts/generate-legal-docs.js
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Configuration - set these via environment variables or edit defaults
const config = {
  organizationName: process.env.ORGANIZATION_NAME || 'Playbot',
  contactEmail: process.env.CONTACT_EMAIL || 'https://github.com/wraithfive/playbot/issues',
  websiteUrl: process.env.WEBSITE_URL || 'https://github.com/wraithfive/playbot',
  lastUpdated: new Date().toLocaleDateString('en-US', { 
    year: 'numeric', 
    month: 'long', 
    day: 'numeric' 
  }),
};

const replacements = {
  '[YOUR NAME/ORGANIZATION]': config.organizationName,
  '[YOUR CONTACT EMAIL]': config.contactEmail,
  '[YOUR WEBSITE]': config.websiteUrl,
  '[CURRENT DATE]': config.lastUpdated,
};

function processTemplate(templatePath, outputPath) {
  console.log(`Processing: ${path.basename(templatePath)}`);
  
  let content = fs.readFileSync(templatePath, 'utf8');
  
  // Remove the template warning comment block at the top
  content = content.replace(/\/\*\*[\s\S]*?\*\/\n\n/m, '');
  
  // Replace all placeholders
  for (const [placeholder, value] of Object.entries(replacements)) {
    const regex = new RegExp(placeholder.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g');
    content = content.replace(regex, value);
  }
  
  fs.writeFileSync(outputPath, content, 'utf8');
  console.log(`✓ Generated: ${path.basename(outputPath)}`);
}

// Main execution
const componentsDir = path.join(__dirname, '..', 'src', 'components');

const files = [
  {
    template: path.join(componentsDir, 'PrivacyPolicy.template.tsx'),
    output: path.join(componentsDir, 'PrivacyPolicy.tsx'),
  },
  {
    template: path.join(componentsDir, 'TermsOfService.template.tsx'),
    output: path.join(componentsDir, 'TermsOfService.tsx'),
  },
];

console.log('Generating legal documents from templates...\n');
console.log('Configuration:');
console.log(`  Organization: ${config.organizationName}`);
console.log(`  Contact Email: ${config.contactEmail}`);
console.log(`  Website: ${config.websiteUrl}`);
console.log(`  Last Updated: ${config.lastUpdated}\n`);

files.forEach(({ template, output }) => {
  if (!fs.existsSync(template)) {
    console.error(`✗ Template not found: ${template}`);
    process.exit(1);
  }
  processTemplate(template, output);
});

console.log('\n✅ All legal documents generated successfully!');
console.log('\nTo customize, set environment variables:');
console.log('  ORGANIZATION_NAME="Your Org"');
console.log('  CONTACT_EMAIL="email@example.com"');
console.log('  WEBSITE_URL="https://example.com"');
