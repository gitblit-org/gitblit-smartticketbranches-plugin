## Gitblit Smart Ticket Branches plugin

*REQUIRES 1.5.0*

The Gitblit Smart Ticket Branches plugin deletes the *ticket/N* branches when a ticket is closed and recreates them if a ticket is re-opened.

### Building against a Gitblit RELEASE

    ant && cp build/target/SmartTicketBranches*.zip /path/to/gitblit/plugins

### Building against a Gitblit SNAPSHOT

    /path/to/dev/gitblit/ant installMoxie
    /path/to/dev/SmartTicketBranches/ant && cp build/target/SmartTicketBranches*.zip /path/to/gitblit/plugins

