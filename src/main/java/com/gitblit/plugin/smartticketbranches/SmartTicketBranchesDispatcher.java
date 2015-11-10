/*
 * Copyright 2014 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.plugin.smartticketbranches;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import ro.fortsoft.pf4j.Extension;

import com.gitblit.git.PatchsetCommand;
import com.gitblit.manager.IGitblit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.QueryBuilder;
import com.gitblit.tickets.QueryResult;
import com.gitblit.tickets.TicketIndexer.Lucene;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.SshCommand;
import com.gitblit.transport.ssh.commands.UsageExample;
import com.gitblit.transport.ssh.commands.UsageExamples;
import com.google.common.collect.Maps;

@Extension
@CommandMetaData(name = "stb", description = "SmartTicketBranches commands", admin = true)
public class SmartTicketBranchesDispatcher extends DispatchCommand {

	@Override
	protected void setup() {
		register(CleanupCommand.class);
	}

	@CommandMetaData(name = "cleanup", description = "Remove ticket branches for closed tickets", admin = true)
	@UsageExamples(examples = {
			@UsageExample(syntax = "${cmd} gitblit.git --dryRun", description = "Test to see what branches would be removed from gitblit.git"),
			@UsageExample(syntax = "${cmd} ALL", description = "Remove all branches for all closed tickets")
	})
	public static class CleanupCommand extends SshCommand {

		@Argument(index = 0, metaVar = "ALL|<REPOSITORY>", required = true, usage = "Repository for cleanup")
		String repository;

		@Option(name = "--dryRun", usage = "Identify but DO NOT remove ticket branches")
		boolean dryRun;

		/**
		 * Remove closed ticket branches.
		 */
		@Override
		public void run() throws Failure {
			log.debug("Removing closed ticket branches for {}", repository);

			IGitblit gitblit = getContext().getGitblit();
			ITicketService tickets = gitblit.getTicketService();

			if (tickets == null) {
				log.warn("No ticket service is configured!");
				return;
			}

			if (!tickets.isReady()) {
				log.warn("Ticket service is not ready!");
				return;
			}

			// query for closed tickets

			final QueryBuilder query = new QueryBuilder();
			if (!"ALL".equalsIgnoreCase(repository)) {
				RepositoryModel r = gitblit.getRepositoryModel(repository);
				if (r == null) {
					throw new UnloggedFailure(1,  String.format("%s is not a repository!", repository));
				}
				query.and(Lucene.rid.matches(r.getRID()));
			} else {
				query.and(Lucene.rid.matches("[* TO *]"));
			}
			query.and(Lucene.status.doesNotMatch(Status.New.toString())).and(Lucene.status.doesNotMatch(Status.Open.toString()));

			final String q = query.build();
			log.debug(q);
			List<QueryResult> closedTickets = tickets.queryFor(q, 0, 0, Lucene.repository.toString(), false);

			if (closedTickets.isEmpty()) {
				stdout.println(String.format("No closed tickets found for '%s'.", repository));
				return;
			}

			// collate the tickets by repository
			Map<String, List<QueryResult>> map = Maps.newTreeMap();
			for (QueryResult ticket : closedTickets) {
				if (!map.containsKey(ticket.repository)) {
					map.put(ticket.repository, new ArrayList<QueryResult>());
				}
				map.get(ticket.repository).add(ticket);
			}

			// remove branches for all closed tickets
			// group the branch removals for performance
			for (Map.Entry<String, List<QueryResult>> entry : map.entrySet()) {
				String repo = entry.getKey();
				List<QueryResult> list = entry.getValue();
				Repository db = gitblit.getRepository(repo);
				if (db == null) {
					log.warn("failed to find git repository {}", repo);
					continue;
				}

				try {
					BatchRefUpdate batch = db.getRefDatabase().newBatchUpdate();

					// setup a reflog ident
					UserModel user = getContext().getClient().getUser();
					PersonIdent ident =	new PersonIdent(String.format("%s/%s", user.getDisplayName(), user.username),
							user.emailAddress == null ? user.username : user.emailAddress);
					batch.setRefLogIdent(ident);

					for (QueryResult ticket : list) {
						final String branch = PatchsetCommand.getTicketBranch(ticket.number);
						try {
							Ref ref = db.getRef(branch);
							if (ref != null) {
								if (dryRun) {
									String msg = String.format("would remove %s:%s (%s)",
											ticket.repository, branch, ticket.status);
									stdout.println(msg);
									continue;
								}

								// queue the branch removal
								batch.addCommand(new ReceiveCommand(ref.getObjectId(), ObjectId.zeroId(), branch));
							}
						} catch (IOException e) {
							log.error("SmartTicketBranches plugin", e);
						}
					}

					// apply all queued branch deletions
					if (!batch.getCommands().isEmpty()) {
						try (RevWalk rw = new RevWalk(db)) {
							batch.execute(rw, NullProgressMonitor.INSTANCE);
							for (ReceiveCommand cmd : batch.getCommands()) {
								switch (cmd.getResult()) {
				    			case OK:
				    				String successMsg = String.format("removed %s:%s",
				    						repo, cmd.getRefName());
				    				log.info(successMsg);
				    				stdout.println(successMsg);
				    				break;
				    			default:
				    				String errorMsg = String.format("failed to remove %s:%s (%s)",
				    						repo, cmd.getRefName(), cmd.getResult());
				    				log.error(errorMsg);
				    				stdout.println(errorMsg);
				    				break;
				    			}
							}
						}
					}
				} catch (IOException e) {
					log.error("SmartTicketBranches plugin", e);
				} finally {
					db.close();
				}
			}
		}
	}
}

