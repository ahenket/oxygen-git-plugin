package com.oxygenxml.git.view.branches;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import com.oxygenxml.git.constants.UIConstants;
import com.oxygenxml.git.service.GitAccess;
import com.oxygenxml.git.service.NoRepositorySelected;
import com.oxygenxml.git.utils.TreeUtil;
import com.oxygenxml.git.view.CoalescedEventUpdater;
import com.oxygenxml.git.view.GitTreeNode;
import com.oxygenxml.git.view.dialog.UIUtil;

import ro.sync.exml.workspace.api.PluginWorkspaceProvider;
import ro.sync.exml.workspace.api.standalone.ui.Tree;

/**
 * Branch management panel. Contains the branches tree (local + remote branches).
 */
public class BranchManagementPanel extends JPanel {
  
  /**
   * Git API access.
   */ 
  private static final GitAccess gitAccess = GitAccess.getInstance();

  /**
   * A field for searching branches in the current repository.
   */
  private JTextField searchBar;
  
  /**
   * The tree in which the branches will be presented.
   */
  private JTree branchesTree;
  
  /**
   * The list with the branches from the current repository.
   */
  private List<String> allBranches;
  
  /**
   * A list with the branches that have been removed from the tree due to
   * filtering.
   */
  private List<String> removedBranches;

  /**
   * Public constructor
   */
  public BranchManagementPanel() {
    createGUI();
  }
  
  /**
   * Creates the tree for the branches in the current repository.
   */
  private void createBranchesTree() {
    allBranches = getBranches();
    
    branchesTree = new Tree(new BranchManagementTreeModel(null, allBranches));
    branchesTree.setCellRenderer(new BranchesTreeCellRenderer());
    branchesTree.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
    branchesTree.setDragEnabled(false);
    
    ToolTipManager.sharedInstance().registerComponent(branchesTree);
  }

  /**
   * Creates the components and adds listeners to some of them.
   */
  private void createGUI() {
    setLayout(new GridBagLayout());
    
    createSearchBar();
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.NORTH;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(0, 5, 0, 5);
    add(searchBar, gbc);
    
    createBranchesTree();
    JScrollPane branchesTreeScrollPane = new JScrollPane(branchesTree); 
    gbc.insets = new Insets(3, 5, 3, 5);
    gbc.gridy++;
    gbc.weightx = 1;
    gbc.weighty = 1;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    add(branchesTreeScrollPane, gbc);
    
    branchesTree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        TreeUtil.expandSingleChildPath(branchesTree, event);
      }
      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        // Nothing
      }
    });
    
    setMinimumSize(new Dimension(UIConstants.PANEL_WIDTH, UIConstants.COMMIT_PANEL_PREF_HEIGHT));
    
    // TODO: this is probably only temporary. Perhaps we should also make the view work fine when Oxygen starts.
    // We should probably populate it when the Git Staging panel refreshes and isAfterRefresh = true. See PanelRefresh.
    setVisible(false); 
    
  }
  
  /**
   * Creates a list with all branches, local and remote, for the current repository.
   * 
   * @return The list of all branches. Never <code>null</code>.
   */
  public List<String> getBranches() {
    List<String> branchList = new ArrayList<>();
    Repository repository = getCurrentRepository();
    if(repository != null) {
      List<Ref> branches = new ArrayList<>();
      branches.addAll(gitAccess.getLocalBranchList());
      branches.addAll(gitAccess.getRemoteBrachListForCurrentRepo());
      branchList = branches
          .stream()
          .map(branch -> rewriteBranchName(branch.getName()))
          .collect(Collectors.toList());
    }
    return branchList;
  }
  
  /**
   * Updates a tree structure with the given branches. 
   * 
   * @param branchList The branches used to generate the nodes.
   */
  private void updateTreeView(List<String> branchList) {
    if (branchesTree != null) {
      Enumeration<TreePath> expandedPaths = TreeUtil.getLastExpandedPaths(branchesTree);
      TreePath[] selectionPaths = branchesTree.getSelectionPaths();

      // Create the tree with the new model
      branchesTree.setModel(
          new BranchManagementTreeModel(
              GitAccess.getInstance().getWorkingCopyName(), 
              branchList));

      // restore last expanded paths after refresh
      TreeUtil.restoreLastExpandedPaths(expandedPaths, branchesTree);
      branchesTree.setSelectionPaths(selectionPaths);
    }
  }
  
  /**
   * @return the current repository or <code>null</code> if there's no repository selected.
   */
  private Repository getCurrentRepository() {
    Repository repo = null;
    try {
      repo = GitAccess.getInstance().getRepository();
    } catch (NoRepositorySelected e) {
      // TODO: this shows an error on loading... We should take care of the loading part,
      // because in other cases we should show this error
//      PluginWorkspaceProvider.getPluginWorkspace().showErrorMessage(e.getMessage(), e);
    }
    return repo;
  }
  
  /**
   * Rewrites the path to the branch in order to explicitly show the branch types, such as "Local" or "Remote".
   * 
   * @param name The branch name to be altered.
   * 
   * @return The new name
   */
  public String rewriteBranchName(String name) {
    String newBranchName;
    name = name.replaceFirst("^(refs[/])", "");
    if (name.contains("heads")) {
      newBranchName = name.replaceFirst("^(heads)", BranchManagementConstants.LOCAL);
    } else {
      newBranchName = name.replaceFirst("^remotes", BranchManagementConstants.REMOTE);
    }
    return newBranchName;
  }
  
  /**
   * Creates the search bar for the branches in the current repository. 
   */
  private void createSearchBar() {
    searchBar = UIUtil.createTextField();
    // TODO: i18n
    searchBar.setText("Search branch");
    searchBar.setForeground(Color.GRAY);
    // TODO: i18n
    searchBar.setToolTipText("Type here the name of the branch you want to find");
    
    searchBar.addFocusListener(new FocusListener() {

      @Override
      public void focusGained(FocusEvent e) {
        // TODO: i18n
        if (searchBar.getText().contentEquals("Search branch")) {
          searchBar.setText("");
        } else {
          searchBar.selectAll();
        }
        updateTreeView(allBranches);
        TreeUtil.expandAllNodes(branchesTree, 0, branchesTree.getRowCount());
        searchBar.setForeground(Color.BLACK);
      }

      @Override
      public void focusLost(FocusEvent e) {
        if(searchBar.getText().isEmpty()) {
          // TODO: i18n
          searchBar.setText("Search Branch");
          searchBar.setForeground(Color.GRAY);
        }
      }
      
    });
    CoalescedEventUpdater updater = new CoalescedEventUpdater(500, () -> {
//      // Version 1 to filter the tree by searching in all branches the text for
//      // filtering and recreating the tree with only those who contain it.
//       searchInTree(searchBar.getText());

      // Version 2 to filter the current tree by adding and removing branches from it.
      BranchManagementTreeModel model = (BranchManagementTreeModel) branchesTree.getModel();
      model.filterTree(branchesTree, searchBar.getText(), removedBranches);
    });
    
    searchBar.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent evt) {
        updater.update();
      }
    });
  }
  /**
   * Searches in tree for the branches that contains a string of characters and updates the tree with those branches.
   * @param text The string to find.
   */
  private void searchInTree(String text) {
    updateTreeView(allBranches);
    List<String> remainingBranches = new ArrayList<>();
    TreeModel model = branchesTree.getModel();
    GitTreeNode root = (GitTreeNode) model.getRoot();
    Enumeration<?> e = root.depthFirstEnumeration();
    while (e.hasMoreElements()) {
      GitTreeNode node = (GitTreeNode) e.nextElement();
      if (node.isLeaf()) {
        String userObject = (String) node.getUserObject();
        if (userObject.contains(text)) {
          TreeNode[] path = node.getPath();
          StringBuilder branchPath = new StringBuilder();
          for (int i = 1; i < path.length; ++i) {
            branchPath.append(path[i].toString());
            if (i + 1 < path.length) {
              branchPath.append("/");
            }
          }
          remainingBranches.add(branchPath.toString());
        }
      }
    }
    updateTreeView(remainingBranches);
    TreeUtil.expandAllNodes(branchesTree, 0, branchesTree.getRowCount());
  }
  
  /**
   * Shows the branch panel with all its components.
   */
  public void showBranches() {
    allBranches = getBranches();
    removedBranches = new ArrayList<>();
    updateTreeView(allBranches);
    TreeUtil.expandAllNodes(branchesTree, 0, branchesTree.getRowCount());
    setVisible(true);
  }
  
}