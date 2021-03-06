package com.oxygenxml.git.view.history.actions;

import java.awt.HeadlessException;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.RepositoryState;

import com.oxygenxml.git.service.GitAccess;
import com.oxygenxml.git.service.GitOperationScheduler;
import com.oxygenxml.git.service.NoRepositorySelected;
import com.oxygenxml.git.translator.Tags;
import com.oxygenxml.git.translator.Translator;
import com.oxygenxml.git.view.branches.BranchesUtil;
import com.oxygenxml.git.view.branches.CreateBranchDialog;

import ro.sync.exml.workspace.api.PluginWorkspaceProvider;
import ro.sync.exml.workspace.api.standalone.ui.OKCancelDialog;

/**
 * Create a new branch starting from a commit in the history table.
 */
public class CreateBranchFromCommitAction extends AbstractAction {
  /**
   * Logger for logging.
   */
  private static final Logger LOGGER = LogManager.getLogger(CreateBranchFromCommitAction.class.getName());
  
  /**
   * The ID of the commit used as a starting point for the new branch.
   */
  private String commitId;
  
  /**
   * Constructor.
   * @param commitId 
   */
  public CreateBranchFromCommitAction(String commitId) {
    super(Translator.getInstance().getTranslation(Tags.CREATE_BRANCH) + "...");
    this.commitId = commitId;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    GitOperationScheduler.getInstance().schedule(() -> {
      try {
        CreateBranchDialog dialog = new CreateBranchDialog(
            Translator.getInstance().getTranslation(Tags.CREATE_BRANCH),
            null,
            BranchesUtil.getLocalBranches());
        if (dialog.getResult() == OKCancelDialog.RESULT_OK) {
          GitAccess.getInstance().checkoutCommitAndCreateBranch(dialog.getBranchName(), commitId);
        }
      } catch (CheckoutConflictException e1) {
        showCannotCheckoutBranchMessage();
      } catch (HeadlessException | GitAPIException | NoRepositorySelected e1) {
        LOGGER.debug(e1);
        PluginWorkspaceProvider.getPluginWorkspace().showErrorMessage(e1.getMessage(), e1);
      }
    });
  }
  
  /**
   * Show a message saying why the branch checkout failed.
   */
  private void showCannotCheckoutBranchMessage() {
    RepositoryState state = null;
    try {
      state = GitAccess.getInstance().getRepository().getRepositoryState();
    } catch (NoRepositorySelected e2) {
      LOGGER.debug(e2, e2);
    }

    if (state != null) {
      String messageTag = Tags.CANNOT_CHECKOUT_NEW_BRANCH;
      switch (state) {
        case SAFE:
          messageTag = Tags.CANNOT_CHECKOUT_NEW_BRANCH_BECAUSE_UNCOMMITTED_CHANGES;
          break;
        case MERGING:
          messageTag = Tags.CANNOT_CHECKOUT_NEW_BRANCH_WHEN_HAVING_CONFLICTS;
          break;
        default:
          break;
      }
      PluginWorkspaceProvider.getPluginWorkspace().showErrorMessage(
          Translator.getInstance().getTranslation(messageTag));
    }
  }

}
