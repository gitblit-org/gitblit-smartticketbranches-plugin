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

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.Extension;

import com.gitblit.extensions.TicketHook;
import com.gitblit.git.PatchsetCommand;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Patchset;
import com.gitblit.servlet.GitblitContext;

/**
 * This hook will automatically remove and re-create the ticket branch on status
 * changes.
 *
 * @author James Moger
 *
 */
@Extension
public class SmartTicketBranchesHook extends TicketHook {

	final String name = getClass().getSimpleName();

	final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void onNewTicket(TicketModel ticket) {
    	// NOOP
    }

    @Override
    public void onUpdateTicket(TicketModel ticket, Change change) {
    	if (!ticket.hasPatchsets()) {
    		// ticket has no patchsets, nothing to do
    		return;
    	}

    	if (!change.isStatusChange()) {
    		// not a status change, nothing to do
    		return;
    	}

		final Patchset ps = ticket.getCurrentPatchset();
    	final String branch = PatchsetCommand.getTicketBranch(ticket.number);
    	final IRepositoryManager repositoryManager = GitblitContext.getManager(IRepositoryManager.class);
    	final Repository repo = repositoryManager.getRepository(ticket.repository);
    	try {
    		switch (change.getStatus()) {
    		case New:
    			// NOOP, new proposal
    			log.debug("new proposal, skipping");
    			break;
    		case Open:
    			/*
    			 *  Open or Re-open: create branch, if not exists
    			 */
    			if (null == repo.getRef(branch)) {
    				log.debug("ticket re-opened, trying to create '{}'", branch);
    				RefUpdate ru = repo.updateRef(branch);
    				ru.setExpectedOldObjectId(ObjectId.zeroId());
    				ru.setNewObjectId(ObjectId.fromString(ps.tip));

    				RevWalk rw = null;
    				try {
    					rw = new RevWalk(repo);
    					RefUpdate.Result result = ru.update(rw);
    					switch (result) {
    					case NEW:
    						log.info(String.format("%s ticket RE-OPENED, created %s:%s",
    								name, ticket.repository, branch));
    						break;
    					default:
    						log.error(String.format("%s failed to re-create %s:%s (%s)",
    								name, ticket.repository, branch, result));
    						break;
    					}
    				} finally {
    					if (rw != null) {
    						rw.release();
    					}
    				}
    			}
    			break;
    		default:
    			/*
    			 * Ticket closed: delete branch, if exists
    			 */
    			log.debug("ticket closed, trying to remove '{}'", branch);
    			RefUpdate ru = repo.updateRef(branch);
    			ru.setExpectedOldObjectId(ObjectId.fromString(ps.tip));
    			ru.setNewObjectId(ObjectId.zeroId());
    			ru.setForceUpdate(true);

    			RefUpdate.Result result = ru.delete();
    			switch (result) {
    			case FORCED:
    				log.info(String.format("%s ticket %s, removed %s:%s",
    						name, change.getStatus(), ticket.repository, branch));
    				break;
    			default:
    				log.error(String.format("%s failed to remove %s:%s (%s)",
    						name, ticket.repository, branch, result));
    				break;
    			}
    		}
    	} catch (IOException e) {
    		log.error(null, e);
    	} finally {
    		if (repo != null) {
    			repo.close();
    		}
    	}
    }
}