import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.plaf.basic.BasicTextUI;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.Base64;

public class Main extends JFrame {
    private static final String LOGIN_USER = "admin";
    private static final String LOGIN_PASS = "notes123";
    private static final Path DATA_FILE = Paths.get("notes_data.txt");

    private static class Note {
        String id, title, content, category;
        long created, modified;
        boolean favorite, deleted;

        Note(String id, String title, String content, String category,
             long created, long modified, boolean favorite, boolean deleted) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.category = category;
            this.created = created;
            this.modified = modified;
            this.favorite = favorite;
            this.deleted = deleted;
        }

        String displayTitle() {
            return title == null || title.trim().isEmpty() ? "Untitled note" : title.trim();
        }

        String preview() {
            String s = content == null ? "" : content.replaceAll("\\s+", " ").trim();
            if (s.isEmpty()) return "No content";
            return s.length() > 85 ? s.substring(0, 85) + "..." : s;
        }
    }

    private final List<Note> notes = new ArrayList<>();
    private Note editing;
    private String currentView = "All Notes";
    private boolean loadingEditor = false;
    private boolean darkTheme = true;

    private Color bg, panel, fieldBg, fg, muted, accent, border;
    private JTextField searchField, titleField;
    private JTextArea contentArea;
    private JComboBox<String> categoryBox;
    private JCheckBox favoriteBox;
    private DefaultListModel<Note> listModel;
    private JList<Note> noteList;
    private JLabel statusLabel, countLabel;
    private JPanel mainPanel, editorPanel, dashboardPanel;
    private CardLayout rightCards;
    private javax.swing.Timer saveTimer, searchTimer;

    public Main() {
        super("Notes Manager");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(950, 620));
        setSize(1120, 740);
        setLocationRelativeTo(null);
        setThemeColors();
        loadNotes();
        showLogin();
    }

    private void showLogin() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(bg);
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(panel);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(30, 38, 30, 38)));
        card.setPreferredSize(new Dimension(370, 390));

        JLabel icon = new JLabel("✎", SwingConstants.CENTER);
        icon.setFont(new Font("SansSerif", Font.BOLD, 42));
        icon.setForeground(accent);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel heading = new JLabel("Notes Manager");
        heading.setFont(new Font("SansSerif", Font.BOLD, 25));
        heading.setForeground(fg);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Your ideas, safely organized");
        subtitle.setForeground(muted);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField username = new JTextField();
        JPasswordField password = new JPasswordField();
        styleField(username);
        styleField(password);
        username.setMaximumSize(new Dimension(300, 42));
        password.setMaximumSize(new Dimension(300, 42));

        JLabel userLabel = label("Username");
        JLabel passLabel = label("Password");
        JButton login = button("Sign in", true);
        login.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel hint = new JLabel("Demo login: admin  |  notes123");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        hint.setForeground(muted);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(icon);
        card.add(Box.createVerticalStrut(5));
        card.add(heading);
        card.add(Box.createVerticalStrut(5));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(24));
        card.add(userLabel);
        card.add(Box.createVerticalStrut(5));
        card.add(username);
        card.add(Box.createVerticalStrut(12));
        card.add(passLabel);
        card.add(Box.createVerticalStrut(5));
        card.add(password);
        card.add(Box.createVerticalStrut(18));
        card.add(login);
        card.add(Box.createVerticalStrut(12));
        card.add(hint);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        root.add(card, gbc);
        setContentPane(root);

        Runnable doLogin = () -> {
            String u = username.getText().trim();
            String p = new String(password.getPassword());
            if (LOGIN_USER.equals(u) && LOGIN_PASS.equals(p)) {
                buildApplication();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Incorrect username or password. Please try again.",
                        "Login failed", JOptionPane.WARNING_MESSAGE);
                password.setText("");
            }
        };
        login.addActionListener(e -> doLogin.run());
        password.addActionListener(e -> doLogin.run());
        getRootPane().setDefaultButton(login);
        setVisible(true);
    }

    private void buildApplication() {
        setThemeColors();
        mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBackground(bg);

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(bg);
        header.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Notes Manager");
        title.setFont(new Font("SansSerif", Font.BOLD, 23));
        title.setForeground(fg);
        JLabel subtitle = new JLabel("Capture ideas. Keep everything organized.");
        subtitle.setForeground(muted);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        brand.add(title);
        brand.add(Box.createVerticalStrut(3));
        brand.add(subtitle);

        JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        headerButtons.setOpaque(false);
        JButton dashboardBtn = button("Dashboard", false);
        JButton allBtn = button("All Notes", false);
        JButton favBtn = button("★ Favorites", false);
        JButton trashBtn = button("Trash", false);
        JButton themeBtn = button("Change Theme", false);
        JButton logoutBtn = button("Log out", false);
        headerButtons.add(dashboardBtn);
        headerButtons.add(allBtn);
        headerButtons.add(favBtn);
        headerButtons.add(trashBtn);
        headerButtons.add(themeBtn);
        headerButtons.add(logoutBtn);
        header.add(brand, BorderLayout.WEST);
        header.add(headerButtons, BorderLayout.EAST);

        searchField = new JTextField();
        searchField.setToolTipText("Search notes by title or content");
        searchField.putClientProperty("JTextField.placeholderText", "Search notes...");
        styleField(searchField);
        JButton newBtn = button("+ New Note", true);
        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(newBtn, BorderLayout.EAST);

        listModel = new DefaultListModel<>();
        noteList = new JList<>(listModel);
        noteList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        noteList.setCellRenderer(new NoteRenderer());
        noteList.setFixedCellHeight(76);
        noteList.setBackground(fieldBg);
        noteList.setForeground(fg);
        JScrollPane listScroll = new JScrollPane(noteList);
        listScroll.setBorder(BorderFactory.createLineBorder(border));
        listScroll.getViewport().setBackground(fieldBg);

        JPanel left = new JPanel(new BorderLayout(0, 10));
        left.setBackground(bg);
        left.setBorder(BorderFactory.createEmptyBorder(0, 16, 12, 8));
        left.setPreferredSize(new Dimension(330, 500));
        left.add(searchRow, BorderLayout.NORTH);
        left.add(listScroll, BorderLayout.CENTER);

        titleField = new JTextField();
        titleField.setFont(new Font("SansSerif", Font.BOLD, 21));
        styleField(titleField);
        contentArea = new JTextArea();
        contentArea.setFont(new Font("SansSerif", Font.PLAIN, 15));
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setMargin(new Insets(14, 14, 14, 14));
        contentArea.setBackground(fieldBg);
        contentArea.setForeground(fg);
        contentArea.setCaretColor(fg);
        contentArea.setBorder(BorderFactory.createEmptyBorder());
        JScrollPane contentScroll = new JScrollPane(contentArea);
        contentScroll.setBorder(BorderFactory.createLineBorder(border));
        contentScroll.getViewport().setBackground(fieldBg);

        categoryBox = new JComboBox<>(new String[]{"Personal", "College", "Projects", "Ideas", "Work", "Other"});
        styleComboBox(categoryBox);
        favoriteBox = new JCheckBox("★ Favorite");
        favoriteBox.setOpaque(false);
        favoriteBox.setForeground(fg);
        JButton pinFavBtn = button("Toggle Favorite", false);
        JButton deleteBtn = button("Move to Trash", false);
        JButton saveBtn = button("Save Now", true);
        JPanel editorTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        editorTools.setOpaque(false);
        editorTools.add(label("Category"));
        editorTools.add(categoryBox);
        editorTools.add(favoriteBox);
        editorTools.add(pinFavBtn);
        editorTools.add(deleteBtn);
        editorTools.add(saveBtn);

        JPanel editorTop = new JPanel(new BorderLayout(0, 10));
        editorTop.setOpaque(false);
        editorTop.add(titleField, BorderLayout.NORTH);
        editorTop.add(editorTools, BorderLayout.SOUTH);

        editorPanel = new JPanel(new BorderLayout(0, 10));
        editorPanel.setBackground(bg);
        editorPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 12, 16));
        editorPanel.add(editorTop, BorderLayout.NORTH);
        editorPanel.add(contentScroll, BorderLayout.CENTER);

        dashboardPanel = new JPanel(new BorderLayout(0, 16));
        dashboardPanel.setBackground(bg);
        dashboardPanel.setBorder(BorderFactory.createEmptyBorder(18, 22, 20, 22));

        rightCards = new CardLayout();
        JPanel rightPanel = new JPanel(rightCards);
        rightPanel.setBackground(bg);
        rightPanel.add(editorPanel, "editor");
        rightPanel.add(dashboardPanel, "dashboard");

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rightPanel);
        split.setDividerLocation(330);
        split.setDividerSize(1);
        split.setBorder(null);
        split.setContinuousLayout(true);

        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(muted);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(7, 16, 9, 8));
        countLabel = new JLabel(" ");
        countLabel.setForeground(muted);
        countLabel.setBorder(BorderFactory.createEmptyBorder(7, 8, 9, 16));
        countLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(bg);
        status.add(statusLabel, BorderLayout.WEST);
        status.add(countLabel, BorderLayout.EAST);

        mainPanel.add(header, BorderLayout.NORTH);
        mainPanel.add(split, BorderLayout.CENTER);
        mainPanel.add(status, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        saveTimer = new javax.swing.Timer(450, e -> saveCurrentNote());
        saveTimer.setRepeats(false);
        searchTimer = new javax.swing.Timer(180, e -> refreshList());
        searchTimer.setRepeats(false);

        DocumentListener autosave = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { scheduleSave(); }
            public void removeUpdate(DocumentEvent e) { scheduleSave(); }
            public void changedUpdate(DocumentEvent e) { scheduleSave(); }
        };
        titleField.getDocument().addDocumentListener(autosave);
        contentArea.getDocument().addDocumentListener(autosave);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { searchTimer.restart(); }
            public void removeUpdate(DocumentEvent e) { searchTimer.restart(); }
            public void changedUpdate(DocumentEvent e) { searchTimer.restart(); }
        });

        noteList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Note n = noteList.getSelectedValue();
                if (n != null) openNote(n);
            }
        });
        newBtn.addActionListener(e -> createNote());
        saveBtn.addActionListener(e -> saveCurrentNote());
        pinFavBtn.addActionListener(e -> toggleFavorite());
        favoriteBox.addActionListener(e -> {
            if (!loadingEditor && editing != null) {
                editing.favorite = favoriteBox.isSelected();
                editing.modified = System.currentTimeMillis();
                saveNotes();
                refreshList();
                updateStatus();
            }
        });
        deleteBtn.addActionListener(e -> moveToTrash());
        dashboardBtn.addActionListener(e -> showDashboard());
        allBtn.addActionListener(e -> setView("All Notes"));
        favBtn.addActionListener(e -> setView("Favorites"));
        trashBtn.addActionListener(e -> setView("Trash"));
        themeBtn.addActionListener(e -> {
            darkTheme = !darkTheme;
            setThemeColors();
            applyThemeToApp();
        });
        logoutBtn.addActionListener(e -> {
            saveCurrentNote();
            showLogin();
        });
        categoryBox.addActionListener(e -> {
            if (!loadingEditor && editing != null) {
                editing.category = (String) categoryBox.getSelectedItem();
                editing.modified = System.currentTimeMillis();
                saveNotes();
                refreshList();
                updateStatus();
            }
        });

        installShortcuts();
        refreshList();
        showDashboard();
        setVisible(true);
    }

    private void installShortcuts() {
        JRootPane root = getRootPane();
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        bind(root, "newNote", KeyStroke.getKeyStroke(KeyEvent.VK_N, mask), this::createNote);
        bind(root, "search", KeyStroke.getKeyStroke(KeyEvent.VK_F, mask), () -> {
            searchField.requestFocusInWindow();
            searchField.selectAll();
        });
        bind(root, "save", KeyStroke.getKeyStroke(KeyEvent.VK_S, mask), this::saveCurrentNote);
        bind(root, "delete", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), this::moveToTrash);
        bind(root, "dashboard", KeyStroke.getKeyStroke(KeyEvent.VK_H, mask), this::showDashboard);
    }

    private void bind(JRootPane root, String name, KeyStroke key, Runnable action) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
        root.getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    private void createNote() {
        if (saveTimer != null && saveTimer.isRunning()) saveTimer.stop();
        saveCurrentNote();
        long now = System.currentTimeMillis();
        Note n = new Note(UUID.randomUUID().toString(), "", "", "Personal", now, now, false, false);
        notes.add(n);
        saveNotes();
        currentView = "All Notes";
        searchField.setText("");
        refreshList();
        openNote(n);
        titleField.requestFocusInWindow();
    }

    private void openNote(Note n) {
        if (n.deleted) return;
        editing = n;
        loadingEditor = true;
        titleField.setText(n.title);
        contentArea.setText(n.content);
        categoryBox.setSelectedItem(validCategory(n.category));
        favoriteBox.setSelected(n.favorite);
        titleField.setEnabled(true);
        contentArea.setEnabled(true);
        categoryBox.setEnabled(true);
        favoriteBox.setEnabled(true);
        loadingEditor = false;
        rightCards.show((JPanel) editorPanel.getParent(), "editor");
        statusLabel.setText("Editing: " + n.displayTitle());
        updateStatus();
    }

    private String validCategory(String c) {
        if (c == null) return "Other";
        for (int i = 0; i < categoryBox.getItemCount(); i++)
            if (categoryBox.getItemAt(i).equals(c)) return c;
        return "Other";
    }

    private void scheduleSave() {
        if (!loadingEditor && editing != null && saveTimer != null) saveTimer.restart();
    }

    private void saveCurrentNote() {
        if (editing == null || loadingEditor || titleField == null) return;
        if (saveTimer != null && saveTimer.isRunning()) saveTimer.stop();
        String t = titleField.getText();
        String c = contentArea.getText();
        String cat = (String) categoryBox.getSelectedItem();
        boolean changed = !Objects.equals(editing.title, t)
                || !Objects.equals(editing.content, c)
                || !Objects.equals(editing.category, cat)
                || editing.favorite != favoriteBox.isSelected();
        if (!changed) return;
        editing.title = t;
        editing.content = c;
        editing.category = cat == null ? "Other" : cat;
        editing.favorite = favoriteBox.isSelected();
        editing.modified = System.currentTimeMillis();
        saveNotes();
        refreshList();
        statusLabel.setText("Saved: " + editing.displayTitle());
        updateStatus();
    }

    private void toggleFavorite() {
        if (editing == null) {
            JOptionPane.showMessageDialog(this, "Open a note first.");
            return;
        }
        saveCurrentNote();
        editing.favorite = !editing.favorite;
        editing.modified = System.currentTimeMillis();
        loadingEditor = true;
        favoriteBox.setSelected(editing.favorite);
        loadingEditor = false;
        saveNotes();
        refreshList();
        updateStatus();
    }

    private void moveToTrash() {
        if (currentView.equals("Trash")) {
            permanentlyDelete();
            return;
        }
        if (editing == null) {
            JOptionPane.showMessageDialog(this, "Select a note first.");
            return;
        }
        saveCurrentNote();
        int answer = JOptionPane.showConfirmDialog(this,
                "Move \"" + editing.displayTitle() + "\" to Trash?",
                "Confirm delete", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) return;
        editing.deleted = true;
        editing.modified = System.currentTimeMillis();
        Note deleted = editing;
        editing = null;
        saveNotes();
        refreshList();
        clearEditor();
        statusLabel.setText("Note moved to Trash. Open Trash to restore or delete permanently.");
    }

    private void permanentlyDelete() {
        Note selected = noteList.getSelectedValue();
        if (selected == null || !selected.deleted) {
            JOptionPane.showMessageDialog(this, "Select a note in Trash first.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Permanently delete \"" + selected.displayTitle() + "\"?",
                "Permanent delete", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            notes.removeIf(n -> n.id.equals(selected.id));
            if (editing != null && editing.id.equals(selected.id)) editing = null;
            saveNotes();
            refreshList();
            clearEditor();
            statusLabel.setText("Note permanently deleted.");
        }
    }

    private void restoreSelected() {
        Note selected = noteList.getSelectedValue();
        if (selected == null || !selected.deleted) {
            JOptionPane.showMessageDialog(this, "Select a note in Trash first.");
            return;
        }
        selected.deleted = false;
        selected.modified = System.currentTimeMillis();
        saveNotes();
        setView("All Notes");
        refreshList();
        openNote(selected);
        statusLabel.setText("Note restored.");
    }

    private void clearEditor() {
        loadingEditor = true;
        titleField.setText("");
        contentArea.setText("");
        favoriteBox.setSelected(false);
        loadingEditor = false;
        statusLabel.setText("No note selected");
    }

    private void setView(String view) {
        saveCurrentNote();
        currentView = view;
        refreshList();
        if ("Trash".equals(view)) {
            JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT));
            tools.setOpaque(false);
            // Actions are also available from the buttons below the editor.
            statusLabel.setText("Trash: select a deleted note, then choose Restore or Delete permanently.");
        } else if ("Favorites".equals(view)) {
            statusLabel.setText("Showing favorite notes");
        } else {
            statusLabel.setText("Showing all active notes");
        }
        if ("Trash".equals(view)) showTrashDashboard();
        else if (editing == null || editing.deleted || ("Favorites".equals(view) && !editing.favorite)) showDashboard();
    }

    private void showDashboard() {
        saveCurrentNote();
        refreshDashboard();
        rightCards.show((JPanel) editorPanel.getParent(), "dashboard");
    }

    private void showTrashDashboard() {
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(bg);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JLabel heading = new JLabel("Trash");
        heading.setFont(new Font("SansSerif", Font.BOLD, 25));
        heading.setForeground(fg);
        JLabel text = new JLabel("<html>Deleted notes appear here. Select a note on the left, then use Restore or Delete permanently.</html>");
        text.setForeground(muted);
        JButton restore = button("Restore selected", true);
        JButton permanent = button("Delete permanently", false);
        restore.addActionListener(e -> restoreSelected());
        permanent.addActionListener(e -> permanentlyDelete());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(restore);
        actions.add(permanent);
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(heading);
        top.add(Box.createVerticalStrut(12));
        top.add(text);
        content.add(top, BorderLayout.NORTH);
        content.add(actions, BorderLayout.CENTER);
        dashboardPanel.removeAll();
        dashboardPanel.add(content, BorderLayout.CENTER);
        dashboardPanel.revalidate();
        dashboardPanel.repaint();
        rightCards.show((JPanel) editorPanel.getParent(), "dashboard");
    }

    private void refreshDashboard() {
        if (dashboardPanel == null) return;
        dashboardPanel.removeAll();
        dashboardPanel.setLayout(new BorderLayout(0, 18));
        JLabel heading = new JLabel("Your Dashboard");
        heading.setFont(new Font("SansSerif", Font.BOLD, 27));
        heading.setForeground(fg);
        JLabel sub = new JLabel("A quick overview of your notes and ideas.");
        sub.setForeground(muted);
        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(heading);
        titleBlock.add(Box.createVerticalStrut(5));
        titleBlock.add(sub);

        long active = notes.stream().filter(n -> !n.deleted).count();
        long fav = notes.stream().filter(n -> !n.deleted && n.favorite).count();
        long trash = notes.stream().filter(n -> n.deleted).count();
        long categories = notes.stream().filter(n -> !n.deleted).map(n -> n.category).distinct().count();

        JPanel stats = new JPanel(new GridLayout(1, 4, 12, 12));
        stats.setOpaque(false);
        stats.add(statCard("Total Notes", String.valueOf(active), "All saved notes"));
        stats.add(statCard("Favorites", String.valueOf(fav), "Important notes"));
        stats.add(statCard("In Trash", String.valueOf(trash), "Deleted notes"));
        stats.add(statCard("Categories", String.valueOf(categories), "Ways to organize"));

        JPanel recent = new JPanel();
        recent.setBackground(panel);
        recent.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)));
        recent.setLayout(new BoxLayout(recent, BoxLayout.Y_AXIS));
        JLabel recentHeading = new JLabel("Recently edited");
        recentHeading.setFont(new Font("SansSerif", Font.BOLD, 17));
        recentHeading.setForeground(fg);
        recent.add(recentHeading);
        recent.add(Box.createVerticalStrut(12));
        List<Note> recentNotes = notes.stream().filter(n -> !n.deleted)
                .sorted(Comparator.comparingLong((Note n) -> n.modified).reversed()).limit(6).toList();
        if (recentNotes.isEmpty()) {
            JLabel empty = new JLabel("No notes yet. Click + New Note to create your first note.");
            empty.setForeground(muted);
            recent.add(empty);
        } else {
            for (Note n : recentNotes) {
                JLabel row = new JLabel((n.favorite ? "★  " : "") + n.displayTitle()
                        + "   ·   " + n.category + "   ·   " + formatDate(n.modified));
                row.setForeground(fg);
                row.setBorder(BorderFactory.createEmptyBorder(7, 0, 7, 0));
                row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                row.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e) {
                        currentView = "All Notes";
                        refreshList();
                        openNote(n);
                    }
                });
                recent.add(row);
            }
        }

        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setOpaque(false);
        center.add(stats, BorderLayout.NORTH);
        center.add(recent, BorderLayout.CENTER);

        JButton create = button("+ Create a note", true);
        create.addActionListener(e -> createNote());
        JButton browse = button("Browse all notes", false);
        browse.addActionListener(e -> setView("All Notes"));
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bottom.setOpaque(false);
        bottom.add(create);
        bottom.add(browse);

        dashboardPanel.setBorder(BorderFactory.createEmptyBorder(22, 22, 20, 22));
        dashboardPanel.add(titleBlock, BorderLayout.NORTH);
        dashboardPanel.add(center, BorderLayout.CENTER);
        dashboardPanel.add(bottom, BorderLayout.SOUTH);
        dashboardPanel.revalidate();
        dashboardPanel.repaint();
    }

    private JPanel statCard(String title, String number, String caption) {
        JPanel card = new JPanel();
        card.setBackground(panel);
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(16, 15, 16, 15)));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setForeground(muted);
        t.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JLabel n = new JLabel(number);
        n.setForeground(accent);
        n.setFont(new Font("SansSerif", Font.BOLD, 28));
        JLabel c = new JLabel(caption);
        c.setForeground(muted);
        c.setFont(new Font("SansSerif", Font.PLAIN, 11));
        card.add(t);
        card.add(Box.createVerticalStrut(7));
        card.add(n);
        card.add(Box.createVerticalStrut(5));
        card.add(c);
        return card;
    }

    private void refreshList() {
        if (listModel == null) return;
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<Note> visible = notes.stream()
                .filter(n -> {
                    if ("Trash".equals(currentView)) return n.deleted;
                    if (n.deleted) return false;
                    if ("Favorites".equals(currentView) && !n.favorite) return false;
                    return true;
                })
                .filter(n -> query.isEmpty()
                        || n.displayTitle().toLowerCase(Locale.ROOT).contains(query)
                        || (n.content != null && n.content.toLowerCase(Locale.ROOT).contains(query))
                        || (n.category != null && n.category.toLowerCase(Locale.ROOT).contains(query)))
                .sorted(Comparator.comparingLong((Note n) -> n.modified).reversed())
                .toList();

        Note selected = editing;
        listModel.clear();
        for (Note n : visible) listModel.addElement(n);
        if (selected != null) {
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).id.equals(selected.id)) {
                    noteList.setSelectedIndex(i);
                    break;
                }
            }
        }
        if (countLabel != null) countLabel.setText(visible.size() + " notes shown");
    }

    private void updateStatus() {
        if (editing == null) {
            countLabel.setText(notes.stream().filter(n -> !n.deleted).count() + " total notes");
            return;
        }
        int words = contentArea.getText().isBlank() ? 0 : contentArea.getText().trim().split("\\s+").length;
        countLabel.setText(words + " words  ·  Created " + formatDate(editing.created)
                + "  ·  Modified " + formatDate(editing.modified));
    }

    private class NoteRenderer extends JPanel implements ListCellRenderer<Note> {
        private final JLabel title = new JLabel();
        private final JLabel preview = new JLabel();
        private final JLabel meta = new JLabel();

        NoteRenderer() {
            setLayout(new BorderLayout(4, 5));
            setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 10));
            JPanel text = new JPanel(new GridLayout(2, 1, 0, 4));
            text.setOpaque(false);
            title.setFont(new Font("SansSerif", Font.BOLD, 13));
            preview.setFont(new Font("SansSerif", Font.PLAIN, 11));
            meta.setFont(new Font("SansSerif", Font.PLAIN, 10));
            add(text, BorderLayout.CENTER);
            text.add(title);
            text.add(preview);
            add(meta, BorderLayout.SOUTH);
        }

        public Component getListCellRendererComponent(JList<? extends Note> list, Note n,
                                                       int index, boolean selected, boolean focus) {
            title.setText((n.favorite ? "★  " : "") + n.displayTitle());
            preview.setText(n.preview());
            meta.setText((n.category == null ? "Other" : n.category) + "  ·  " + formatDate(n.modified)
                    + (n.deleted ? "  ·  Deleted" : ""));
            Color fill = selected ? accent : fieldBg;
            Color textColor = selected ? Color.WHITE : fg;
            setBackground(fill);
            title.setForeground(textColor);
            preview.setForeground(selected ? Color.WHITE : muted);
            meta.setForeground(selected ? Color.WHITE : muted);
            return this;
        }
    }

    private void applyThemeToApp() {
        // Rebuild the interface so every component uses the selected palette.
        // Notes remain saved in notes_data.txt.
        if (saveTimer != null) saveCurrentNote();
        buildApplication();
    }

    private void setThemeColors() {
        if (darkTheme) {
            bg = new Color(0x191B20);
            panel = new Color(0x23262D);
            fieldBg = new Color(0x20232A);
            fg = new Color(0xF0F2F5);
            muted = new Color(0xA6ADBB);
            accent = new Color(0x6C8CFF);
            border = new Color(0x3B414D);
        } else {
            bg = new Color(0xF2F4F8);
            panel = new Color(0xFFFFFF);
            fieldBg = new Color(0xFFFFFF);
            fg = new Color(0x242936);
            muted = new Color(0x687083);
            accent = new Color(0x385DCE);
            border = new Color(0xD8DDE7);
        }
    }

    private void styleField(JTextField field) {
        // Use Swing's basic UI delegate so Windows themes cannot repaint fields white.
        field.setUI(new BasicTextFieldUI());
        field.setBackground(fieldBg);
        field.setForeground(fg);
        field.setCaretColor(accent);
        field.setSelectionColor(accent);
        field.setSelectedTextColor(Color.WHITE);
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        field.setOpaque(true);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1),
                BorderFactory.createEmptyBorder(9, 11, 9, 11)));
    }

    private void styleComboBox(JComboBox<String> combo) {
        // BasicComboBoxUI respects the colours set below on Windows and Linux.
        combo.setUI(new BasicComboBoxUI());
        combo.setBackground(fieldBg);
        combo.setForeground(fg);
        combo.setFont(new Font("SansSerif", Font.PLAIN, 13));
        combo.setOpaque(true);
        combo.setFocusable(true);
        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1),
                BorderFactory.createEmptyBorder(5, 7, 5, 7)));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setOpaque(true);
                setBackground(isSelected ? accent : fieldBg);
                setForeground(isSelected ? Color.WHITE : fg);
                setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
                return this;
            }
        });
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        return l;
    }

    private JButton button(String text, boolean primary) {
        JButton b = new JButton(text);
        // BasicButtonUI prevents the operating-system theme from forcing a white button.
        b.setUI(new BasicButtonUI());
        b.setFocusPainted(false);
        b.setContentAreaFilled(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? accent : border),
                BorderFactory.createEmptyBorder(9, 12, 9, 12)));
        b.setBackground(primary ? accent : panel);
        b.setForeground(primary ? Color.WHITE : fg);
        b.setOpaque(true);
        b.setBorderPainted(true);
        return b;
    }

    private String formatDate(long ms) {
        if (ms <= 0) return "Unknown date";
        return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
    }

    private String enc(String s) {
        return Base64.getEncoder().encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
    }

    private String dec(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }

    private void saveNotes() {
        List<String> lines = new ArrayList<>();
        for (Note n : notes) {
            lines.add(String.join("\t", enc(n.id), enc(n.title), enc(n.content), enc(n.category),
                    Long.toString(n.created), Long.toString(n.modified),
                    Boolean.toString(n.favorite), Boolean.toString(n.deleted)));
        }
        try {
            Files.write(DATA_FILE, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save notes:\n" + ex.getMessage(),
                    "Storage error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadNotes() {
        notes.clear();
        if (!Files.exists(DATA_FILE)) return;
        try {
            for (String line : Files.readAllLines(DATA_FILE, StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1);
                if (p.length != 8) continue;
                notes.add(new Note(dec(p[0]), dec(p[1]), dec(p[2]), dec(p[3]),
                        Long.parseLong(p[4]), Long.parseLong(p[5]),
                        Boolean.parseBoolean(p[6]), Boolean.parseBoolean(p[7])));
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Some saved notes could not be loaded:\n" + ex.getMessage(),
                    "Storage warning", JOptionPane.WARNING_MESSAGE);
        }
    }

    @Override
    public void dispose() {
        if (saveTimer != null && titleField != null) saveCurrentNote();
        super.dispose();
    }

    public static void main(String[] args) {
        try {
            // Use a cross-platform Swing look and feel; the Windows system theme can
            // override custom colours and make fields/buttons appear white.
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { }
        SwingUtilities.invokeLater(Main::new);
    }
}