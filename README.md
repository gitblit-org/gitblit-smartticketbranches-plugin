## Gitblit Smart Ticket Branches plugin

*REQUIRES 1.5.0*

The Gitblit Smart Ticket Branches plugin provides tools to automatically cleanup closed ticket branches.

1. A ticket hook to delete the *ticket/N* branch on closing a ticket and to re-create it on re-opening a ticket
2. An SSH command to cleanup closed ticket branches

### Installation

This plugin is referenced in the Gitblit Plugin Registry and you may install it using SSH with an administrator account.

    ssh host plugin refresh
    ssh host plugin install SmartTicketBranches
    ssh host plugin ls

Alternatively, you can download the zip from [here](http://plugins.gitblit.com) manually copy it to your `${baseFolder}/plugins` directory.

### Usage

#### Ticket Hook

The ticket hook is automatic.  Just install the plugin and the hook will manage your ticket branches for you automatically.

#### SSH Command

The SSH command requires administrator permissions.

    ssh host stb cleanup ALL|&lt;REPOSITORY&gt; [--dryRun]

### Building against a Gitblit RELEASE

    ant && cp build/target/SmartTicketBranches*.zip /path/to/gitblit/plugins

### Building against a Gitblit SNAPSHOT

    /path/to/dev/gitblit/ant installMoxie
    /path/to/dev/SmartTicketBranches/ant && cp build/target/SmartTicketBranches*.zip /path/to/gitblit/plugins

