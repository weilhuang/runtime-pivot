package com.runtime.pivot.plugin.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.Tree;
import com.runtime.pivot.plugin.core.RuntimePivotOpampService;
import com.runtime.pivot.protocol.PivotCommands;
import com.runtime.pivot.protocol.proto.ClassLoaderNode;
import com.runtime.pivot.protocol.proto.ClassLoaderTreeResult;
import com.runtime.pivot.protocol.proto.CommandResult;
import com.runtime.pivot.protocol.proto.LoadedClassInfo;
import com.runtime.pivot.protocol.proto.LoadedClassesResult;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.Map;

public final class ClassesPanel extends JBPanel<ClassesPanel> {
    private final RuntimePivotOpampService service;
    private final ConsoleView consoleView;
    private final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode("ClassLoaders"));
    private final DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"Class", "Loader"}, 0);

    public ClassesPanel(Project project, RuntimePivotOpampService service, ConsoleView consoleView) {
        super(new BorderLayout());
        this.service = service;
        this.consoleView = consoleView;
        Tree tree = new Tree(treeModel);
        JBTable table = new JBTable(tableModel);
        JButton loaders = new JButton("Load ClassLoaders");
        JButton classes = new JButton("Load classes");
        loaders.addActionListener(event -> run(PivotCommands.CLASS_LOADERS, this::showLoaders));
        classes.addActionListener(event -> run(PivotCommands.CLASS_LOADED, this::showClasses));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(loaders);
        buttons.add(classes);
        add(buttons, BorderLayout.NORTH);
        add(new JBScrollPane(tree), BorderLayout.WEST);
        add(new JBScrollPane(table), BorderLayout.CENTER);
    }

    private void run(String command, ResultConsumer consumer) {
        service.sendCommand(command, new byte[0], 15_000L).whenComplete((result, error) ->
                ApplicationManager.getApplication().invokeLater(() -> {
            if (error != null) {
                consoleView.print(error.getMessage() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
                return;
            }
            try {
                consumer.accept(result);
            } catch (Exception e) {
                consoleView.print(e.getMessage() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
            }
        }));
    }

    private void showLoaders(CommandResult result) throws Exception {
        ClassLoaderTreeResult tree = ClassLoaderTreeResult.parseFrom(result.getPayload());
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("ClassLoaders");
        Map<String, DefaultMutableTreeNode> nodes = new HashMap<>();
        for (ClassLoaderNode node : tree.getNodesList()) {
            DefaultMutableTreeNode swing = new DefaultMutableTreeNode(node.getName() + " [" + node.getLoaderId() + "]");
            nodes.put(node.getLoaderId(), swing);
        }
        for (ClassLoaderNode node : tree.getNodesList()) {
            DefaultMutableTreeNode swing = nodes.get(node.getLoaderId());
            DefaultMutableTreeNode parent = nodes.get(node.getParentId());
            if (parent == null) {
                root.add(swing);
            } else {
                parent.add(swing);
            }
        }
        treeModel.setRoot(root);
        consoleView.print("Loaded " + tree.getNodesCount() + " ClassLoader nodes\n", ConsoleViewContentType.NORMAL_OUTPUT);
    }

    private void showClasses(CommandResult result) throws Exception {
        LoadedClassesResult loaded = LoadedClassesResult.parseFrom(result.getPayload());
        tableModel.setRowCount(0);
        for (LoadedClassInfo info : loaded.getClassesList()) {
            tableModel.addRow(new Object[]{info.getClassName(), info.getLoaderId()});
        }
        consoleView.print("Loaded " + loaded.getClassesCount() + " classes (truncated=" + loaded.getTruncated() + ")\n",
                ConsoleViewContentType.NORMAL_OUTPUT);
    }

    private interface ResultConsumer {
        void accept(CommandResult result) throws Exception;
    }
}
