const core = require('@actions/core');
const exec = require('@actions/exec');
const os = require('os');
const fs = require('fs');
const path = require('path');

// Helper function to execute shell commands and capture output
async function getCommandOutput(command, args = []) {
    let output = '';
    let error = '';
    const options = {
        listeners: {
            stdout: (data) => { output += data.toString(); },
            stderr: (data) => { error += data.toString(); }
        },
        silent: true, // Don't print to console unless we want to
        ignoreReturnCode: true // Handle errors manually
    };

    const exitCode = await exec.exec(command, args, options);
    if (exitCode !== 0 && error) {
        // core.warning(`Command '${command} ${args.join(' ')}' failed with exit code ${exitCode}: ${error.trim()}`);
        // For some commands, stderr might contain useful info even on success (e.g. java -version)
        // or it might be an actual error. We'll let the calling function decide.
    }
    // If error is empty but exitCode is not 0, it might be a silent failure or handled by the command itself.
    // If output is empty but error is not, return error (e.g. java -version prints to stderr)
    return { stdout: output.trim(), stderr: error.trim(), exitCode };
}

// Helper to format multi-line output for logging
function formatBlock(text, indent = '    ▶ ') {
    return text.split('\n').map(line => `${indent}${line}`).join('\n');
}

async function run() {
    try {
        const showExtendedInfo = core.getInput('show_extended_info') === 'true';

        // 1. Generate Timestamp (and set as output)
        core.startGroup('🕒 Generating Timestamp');
        const now = new Date();
        const year = now.getUTCFullYear();
        const month = String(now.getUTCMonth() + 1).padStart(2, '0');
        const day = String(now.getUTCDate()).padStart(2, '0');
        const hours = String(now.getUTCHours()).padStart(2, '0');
        const minutes = String(now.getUTCMinutes()).padStart(2, '0');
        const seconds = String(now.getUTCSeconds()).padStart(2, '0');
        const timestamp = `${year}-${month}-${day} ${hours}:${minutes}:${seconds} UTC`;
        core.setOutput('formatted_run_timestamp', timestamp);
        console.log(`Generated Timestamp: ${timestamp}`);
        core.endGroup();

        // 2. Display Operating System Information
        core.startGroup('🖥️ Operating System Details');
        console.log("========================================");
        console.log(`  ACTION TIME:    ${timestamp}`);
        console.log(`  KERNEL INFO:    ${os.type()} ${os.release()} ${os.version()}`); // More direct os module usage
        console.log(`  ARCHITECTURE:   ${os.arch()}`);
        console.log(`  RUNNER OS:      ${process.env.RUNNER_OS}`); // process.env.RUNNER_OS is more reliable
        console.log(`  HOSTNAME:       ${os.hostname()}`);
        console.log(`  TOTAL MEMORY:   ${(os.totalmem() / (1024 * 1024 * 1024)).toFixed(2)} GB`);
        console.log(`  FREE MEMORY:    ${(os.freemem() / (1024 * 1024 * 1024)).toFixed(2)} GB`);


        if (process.env.RUNNER_OS === "Linux") {
            let distroInfo = 'N/A';
            if (fs.existsSync('/etc/os-release')) {
                const osRelease = fs.readFileSync('/etc/os-release', 'utf8');
                distroInfo = osRelease.split('\n').filter(line => line.startsWith('PRETTY_NAME=')).map(line => line.split('=')[1].replace(/"/g, '')).join(', ');
                if (!distroInfo) { // Fallback if PRETTY_NAME is not found
                    distroInfo = osRelease.split('\n').filter(line => line.startsWith('NAME=') || line.startsWith('VERSION=')).map(line => line.split('=')[1].replace(/"/g, '')).join(' ');
                }
            } else {
                const lsb = await getCommandOutput('lsb_release', ['-a']);
                if (lsb.exitCode === 0 && lsb.stdout) distroInfo = "\n" + formatBlock(lsb.stdout);
            }
            console.log(`  DISTRO INFO:    ${distroInfo}`);
        } else if (process.env.RUNNER_OS === "macOS") {
            const swVers = await getCommandOutput('sw_vers');
            if (swVers.exitCode === 0 && swVers.stdout) console.log(formatBlock(swVers.stdout, "  MACOS VERSION:\n    ▶ "));
        } else if (process.env.RUNNER_OS === "Windows") {
            // PowerShell command is more complex, might be better to simplify or use specific properties
            const psInfo = await getCommandOutput('powershell', ['-Command', "Get-ComputerInfo | Select-Object OsName, OsVersion, OsArchitecture, CsManufacturer, CsModel | Format-List"]);
            if (psInfo.exitCode === 0 && psInfo.stdout) console.log(formatBlock(psInfo.stdout, "  WINDOWS DETAILS:\n    ▶ "));
        }
        console.log("========================================");
        core.endGroup();

        // 3. Configure Git Safe Directory and Display Repository Information
        core.startGroup('🌳 Git Repository Details');
        const workspace = process.env.GITHUB_WORKSPACE;
        if (workspace) {
            await exec.exec('git', ['config', '--global', '--add', 'safe.directory', workspace]);
            console.log(`✅ Added ${workspace} to git safe.directory`);
        } else {
            core.warning('GITHUB_WORKSPACE not set. Skipping git safe.directory configuration.');
        }
        console.log("========================================");
        console.log(`  REPOSITORY:       ${process.env.GITHUB_REPOSITORY}`);
        const branchName = process.env.GITHUB_REF_NAME || (await getCommandOutput('git', ['rev-parse', '--abbrev-ref', 'HEAD'])).stdout;
        console.log(`  BRANCH:           ${branchName}`);
        const commitSha = process.env.GITHUB_SHA || (await getCommandOutput('git', ['rev-parse', 'HEAD'])).stdout;
        console.log(`  COMMIT SHA:       ${commitSha}`);
        console.log(`  SHORT SHA:        ${commitSha.substring(0, 7)}`);

        const author = (await getCommandOutput('git', ['log', '-1', '--pretty=format:%an <%ae>'])).stdout;
        console.log(`  AUTHOR:           ${author}`);
        const commitDate = (await getCommandOutput('git', ['log', '-1', '--pretty=format:%ad', '--date=format:%Y-%m-%d %H:%M:%S %Z'])).stdout;
        console.log(`  COMMIT DATE:      ${commitDate}`);
        const remoteUrl = (await getCommandOutput('git', ['remote', 'get-url', 'origin'])).stdout || 'N/A';
        console.log(`  REMOTE URL:       ${remoteUrl}`);
        const tagsAtHead = (await getCommandOutput('git', ['tag', '--points-at', 'HEAD'])).stdout.replace(/\n/g, ',').replace(/,$/, '') || 'N/A';
        console.log(`  TAGS AT HEAD:     ${tagsAtHead}`);
        console.log("");
        const commitMessage = (await getCommandOutput('git', ['log', '-1', '--pretty=%B'])).stdout;
        console.log("  COMMIT MESSAGE:");
        console.log(formatBlock(commitMessage, '    '));
        console.log("========================================");
        core.endGroup();


        // 4. Display Java and Directory Information (if enabled)
        if (showExtendedInfo) {
            core.startGroup('☕ Java Environment & 📁 Directory Structure');
            console.log("======================================================");
            console.log("  JAVA DETAILS:");
            const javaVersionCmd = await getCommandOutput('java', ['-version']);
            // java -version often prints to stderr
            if (javaVersionCmd.exitCode === 0 && (javaVersionCmd.stdout || javaVersionCmd.stderr)) {
                console.log("    ▶ Version:");
                console.log(formatBlock(javaVersionCmd.stderr || javaVersionCmd.stdout, '      --> ')); // Prefer stderr for java -version
                console.log(`    ▶ JAVA_HOME:    ${process.env.JAVA_HOME || 'N/A (Not explicitly set or found)'}`);
            } else {
                console.log("    ▶ Java not found in PATH or -version command failed.");
            }
            console.log("");
            console.log("  DIRECTORY TREE (current workspace, max depth 3):");
            try {
                const tree = await getCommandOutput('tree', ['-L', '3', '-a', '-I', '.git|.m2|target|node_modules', workspace || '.']);
                if (tree.exitCode === 0 && tree.stdout) {
                    console.log(formatBlock(tree.stdout, '    '));
                } else if (process.env.RUNNER_OS === "Windows") {
                    console.log("    ▶ 'tree' command failed or not found. Using 'cmd /c tree /F /A' for Windows (basic):");
                    const winTree = await getCommandOutput('cmd', ['/c', 'tree', '/F', '/A', workspace || '.']);
                    if (winTree.exitCode === 0 && winTree.stdout) console.log(formatBlock(winTree.stdout, '      '));
                    else console.log("    ▶ Windows 'tree' command also failed or directory is empty.");
                }
                else {
                    console.log("    ▶ 'tree' command not found or failed. Please install it on the runner for a detailed view.");
                    // Add a simple ls -R like fallback if needed, but it can be very verbose.
                    // For brevity, I'll skip the complex awk ls -R here.
                }
            } catch (treeError) {
                core.warning(`Tree command execution error: ${treeError.message}`);
                console.log("    ▶ Error executing tree command.");
            }
            console.log("======================================================");
            core.endGroup();
        }

    } catch (error) {
        core.setFailed(`Action failed: ${error.message}\n${error.stack}`);
    }
}

run();