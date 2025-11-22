/*
Smart Task Manager - Single-file Java Swing app
Features:
 - Add/Edit/Delete tasks
 - Mark complete / Clear completed
 - Filter (All / Completed / Pending)
 - Double-click to edit
 - Custom ListCellRenderer (strikethrough + color)
 - Save / Load tasks to a file (tasks.txt in user home)
 - Undo last delete (stack-based)

How to compile & run:
 1. Save this file as TaskManagerApp.java
 2. javac TaskManagerApp.java
 3. java TaskManagerApp

Author: Generated for user
*/

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;   // ← THIS FIXES YOUR ERROR
import java.awt.event.*;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Stack;


public class TaskManagerApp extends JFrame {
    private DefaultListModel<Task> listModel;
    private JList<Task> taskList;
    private JTextField taskField;
    private JButton addButton, editButton, deleteButton, completeButton, clearCompletedButton, undoButton;
    private JComboBox<String> filterCombo;
    private Stack<Task> undoStack = new Stack<>();

    private static final String DATA_FILE = System.getProperty("user.home") + File.separator + "tasks.txt";

    public TaskManagerApp() {
        super("Smart Task Manager");
        initComponents();
        loadTasks();
        setSize(600, 480);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    private void initComponents() {
        setLayout(new BorderLayout(8,8));

        // Top panel: input + add
        JPanel top = new JPanel(new BorderLayout(6,6));
        taskField = new JTextField();
        addButton = new JButton("Add");
        top.add(taskField, BorderLayout.CENTER);
        top.add(addButton, BorderLayout.EAST);
        top.setBorder(BorderFactory.createEmptyBorder(8,8,0,8));
        add(top, BorderLayout.NORTH);

        // Center: task list
        listModel = new DefaultListModel<>();
        taskList = new JList<>(listModel);
        taskList.setCellRenderer(new TaskCellRenderer());
        taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(taskList);
        scroll.setBorder(BorderFactory.createTitledBorder("Tasks"));
        add(scroll, BorderLayout.CENTER);

        // Right panel: actions
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(BorderFactory.createEmptyBorder(8,0,8,8));

        editButton = new JButton("Edit");
        deleteButton = new JButton("Delete");
        completeButton = new JButton("Toggle Complete");
        clearCompletedButton = new JButton("Clear Completed");
        undoButton = new JButton("Undo Delete");

        Dimension btnSize = new Dimension(140, 30);
        for (JButton b : new JButton[]{editButton, deleteButton, completeButton, clearCompletedButton, undoButton}) {
            b.setMaximumSize(btnSize);
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            right.add(b);
            right.add(Box.createRigidArea(new Dimension(0,8)));
        }

        add(right, BorderLayout.EAST);

        // Bottom: filters
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        filterCombo = new JComboBox<>(new String[]{"All","Pending","Completed"});
        bottom.add(new JLabel("Filter:"));
        bottom.add(filterCombo);
        add(bottom, BorderLayout.SOUTH);

        // Events
        addButton.addActionListener(e -> onAdd());
        taskField.addActionListener(e -> onAdd());

        deleteButton.addActionListener(e -> onDelete());
        editButton.addActionListener(e -> onEdit());
        completeButton.addActionListener(e -> onToggleComplete());
        clearCompletedButton.addActionListener(e -> onClearCompleted());
        undoButton.addActionListener(e -> onUndo());
        filterCombo.addActionListener(e -> refreshView());

        // double-click to edit
        taskList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onEdit();
                }
            }
        });

        // keyboard delete
        taskList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE,0), "delete");
        taskList.getActionMap().put("delete", new AbstractAction(){
            public void actionPerformed(ActionEvent e) { onDelete(); }
        });

        // Window close save
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                saveTasks();
            }
        });
    }

    private void onAdd() {
        String text = taskField.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a task.", "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Task t = new Task(text);
        listModel.addElement(t);
        taskField.setText("");
        refreshView();
    }

    private void onDelete() {
        int idx = taskList.getSelectedIndex();
        if (idx == -1) {
            JOptionPane.showMessageDialog(this, "Select a task to delete.", "No selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Task t = listModel.getElementAt(idx);
        int confirm = JOptionPane.showConfirmDialog(this, "Delete selected task?\n\n" + t.text, "Confirm delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            undoStack.push(t.copy());
            listModel.remove(idx);
            refreshView();
        }
    }

    private void onEdit() {
        int idx = taskList.getSelectedIndex();
        if (idx == -1) {
            JOptionPane.showMessageDialog(this, "Select a task to edit.", "No selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Task t = listModel.getElementAt(idx);
        String input = (String)JOptionPane.showInputDialog(this, "Edit task:", "Edit", JOptionPane.PLAIN_MESSAGE, null, null, t.text);
        if (input != null) {
            input = input.trim();
            if (input.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Task cannot be empty.", "Invalid", JOptionPane.WARNING_MESSAGE);
                return;
            }
            t.text = input;
            listModel.setElementAt(t, idx);
            refreshView();
        }
    }

    private void onToggleComplete() {
        int idx = taskList.getSelectedIndex();
        if (idx == -1) {
            JOptionPane.showMessageDialog(this, "Select a task to toggle complete.", "No selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Task t = listModel.getElementAt(idx);
        t.completed = !t.completed;
        listModel.setElementAt(t, idx);
        refreshView();
    }

    private void onClearCompleted() {
        boolean found = false;
        for (int i = listModel.getSize()-1; i>=0; i--) {
            if (listModel.getElementAt(i).completed) {
                found = true;
                listModel.remove(i);
            }
        }
        if (!found) JOptionPane.showMessageDialog(this, "No completed tasks to clear.", "Info", JOptionPane.INFORMATION_MESSAGE);
        refreshView();
    }

    private void onUndo() {
        if (undoStack.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nothing to undo.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Task t = undoStack.pop();
        listModel.addElement(t);
        refreshView();
    }

    private void refreshView() {
        // For simplicity we'll rebuild a temporary list according to filter and replace the JList model view
        String filter = (String)filterCombo.getSelectedItem();
        DefaultListModel<Task> temp = new DefaultListModel<>();
        for (int i = 0; i < listModel.getSize(); i++) {
            Task t = listModel.getElementAt(i);
            if ("All".equals(filter)) temp.addElement(t);
            else if ("Completed".equals(filter) && t.completed) temp.addElement(t);
            else if ("Pending".equals(filter) && !t.completed) temp.addElement(t);
        }
        taskList.setModel(temp);
    }

    private void loadTasks() {
        try {
            List<Task> tasks = TaskIO.load(DATA_FILE);
            for (Task t : tasks) listModel.addElement(t);
            refreshView();
        } catch (IOException e) {
            // no file yet or read error -> ignore but print stack for developer
            System.err.println("Could not load tasks: " + e.getMessage());
        }
    }

    private void saveTasks() {
        try {
            // Save from underlying full model (not filtered view)
            // Our listModel might be replaced by refreshView, so reconstruct from taskList model if necessary
            List<Task> tasks = new ArrayList<>();
            ListModel<Task> model = taskList.getModel();
            // If taskList is filtered model, we need to get all tasks: keep a separate storage? Simpler: maintain file save from the displayed model + any items in original listModel not in displayed
            // To guarantee we save everything, accumulate unique tasks from both: this.listModel (original reference) and current model
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < this.listModel.getSize(); i++) {
                Task t = this.listModel.getElementAt(i);
                tasks.add(t.copy());
                seen.add(t.uniqueKey());
            }
            for (int i = 0; i < model.getSize(); i++) {
                Task t = model.getElementAt(i);
                if (!seen.contains(t.uniqueKey())) tasks.add(t.copy());
            }

            TaskIO.save(DATA_FILE, tasks);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TaskManagerApp app = new TaskManagerApp();
            app.setVisible(true);
        });
    }

    // ------------------ Supporting classes ------------------

    static class Task {
        String text;
        boolean completed;
        long createdAt;

        Task(String text) {
            this.text = text;
            this.completed = false;
            this.createdAt = System.currentTimeMillis();
        }

        Task(String text, boolean completed, long createdAt) {
            this.text = text;
            this.completed = completed;
            this.createdAt = createdAt;
        }

        public String toString() { return text; }

        Task copy() { return new Task(this.text, this.completed, this.createdAt); }

        String uniqueKey() { return createdAt + "|" + text; }
    }

    static class TaskCellRenderer extends JLabel implements ListCellRenderer<Task> {
        public TaskCellRenderer() { setOpaque(true); }

        @Override
        public Component getListCellRendererComponent(JList<? extends Task> list, Task value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value == null) return this;
            String display = value.text;
            // Use HTML to allow strikethrough
            if (value.completed) display = "<html><span style='color:gray;text-decoration:line-through;'>&#10003; " + escapeHtml(display) + "</span></html>";
            else display = "<html>" + escapeHtml(display) + "</html>";

            setText(display);
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            setFont(list.getFont());
            return this;
        }

        private String escapeHtml(String s) {
            return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
        }
    }

    static class TaskIO {
        // Simple format: createdAt\tcompleted\ttext (tab separated). text has tabs/newlines replaced
        static List<Task> load(String path) throws IOException {
            List<Task> out = new ArrayList<>();
            Path p = Paths.get(path);
            if (!Files.exists(p)) return out;
            try (BufferedReader r = Files.newBufferedReader(p)) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] parts = line.split("\t",3);
                    if (parts.length >= 3) {
                        long created = Long.parseLong(parts[0]);
                        boolean completed = "1".equals(parts[1]);
                        String text = parts[2].replace("\\n","\n");
                        out.add(new Task(text, completed, created));
                    }
                }
            }
            return out;
        }

        static void save(String path, List<Task> tasks) throws IOException {
            Path p = Paths.get(path);
            try (BufferedWriter w = Files.newBufferedWriter(p)) {
                for (Task t : tasks) {
                    String textEsc = t.text.replace("\t"," ").replace("\n","\\n");
                    String line = t.createdAt + "\t" + (t.completed?"1":"0") + "\t" + textEsc;
                    w.write(line);
                    w.newLine();
                }
            }
        }
    }
}
