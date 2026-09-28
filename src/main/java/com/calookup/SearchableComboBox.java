package com.calookup;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.ComboBoxEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A combo box you can type into. The list narrows to entries containing the typed text as you
 * type, arrow keys and the mouse pick from the narrowed list, and Enter picks the first match.
 */
class SearchableComboBox extends JComboBox<String>
{
	private List<String> all = Collections.emptyList();
	private final JTextField editorField;
	private Consumer<String> onSelect = s ->
	{
	};

	/** Set while the model or editor text is being changed by code, so listeners stay quiet. */
	private boolean updating;
	/** The last item chosen through this box, so re-selecting it does not fire again. */
	private String current;

	SearchableComboBox()
	{
		setEditable(true);
		editorField = (JTextField) getEditor().getEditorComponent();

		editorField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				typed();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				typed();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				typed();
			}
		});

		editorField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				// Make the whole name replaceable in one keystroke.
				SwingUtilities.invokeLater(editorField::selectAll);
			}

			@Override
			public void focusLost(FocusEvent e)
			{
				// Put the chosen boss back if the user wandered off mid-search.
				if (!updating)
				{
					setText(current == null ? "" : current);
					resetModel();
				}
			}
		});

		// A click places a caret, which would undo the select-all above and leave the user
		// editing the middle of a boss name. Re-select everything so typing replaces it.
		editorField.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (editorField.getSelectedText() == null || editorField.getSelectedText().length() < editorField.getText().length())
				{
					SwingUtilities.invokeLater(editorField::selectAll);
				}
			}
		});

		addActionListener(e ->
		{
			if (updating)
			{
				return;
			}
			Object selected = getSelectedItem();
			String match = selected instanceof String ? resolve((String) selected) : null;
			if (match == null)
			{
				return;
			}
			String previous = current;
			current = match;
			setText(match);
			resetModel();
			if (!match.equals(previous))
			{
				onSelect.accept(match);
			}
		});
	}

	/** Called when the user picks an entry, with the exact entry text. */
	void setOnSelect(Consumer<String> onSelect)
	{
		this.onSelect = onSelect;
	}

	/** Replaces the full list of entries. Keeps the current choice if it still exists. */
	void setItems(List<String> items)
	{
		all = new ArrayList<>(items);
		resetModel();
		if (current != null && !all.contains(current))
		{
			current = null;
			setText("");
		}
	}

	/** Shows an entry as chosen without firing {@link #setOnSelect}. */
	void setCurrent(String item)
	{
		current = item;
		setText(item == null ? "" : item);
		resetModel();
	}

	@Override
	public void configureEditor(ComboBoxEditor anEditor, Object anItem)
	{
		// Selection changes (arrow keys, mouse) rewrite the editor text; do not treat that as typing.
		boolean was = updating;
		updating = true;
		try
		{
			super.configureEditor(anEditor, anItem);
			// Called from the JComboBox constructor too, before editorField is assigned.
			if (editorField != null && !editorField.hasFocus())
			{
				editorField.setCaretPosition(0);
			}
		}
		finally
		{
			updating = was;
		}
	}

	private void typed()
	{
		if (updating)
		{
			return;
		}
		SwingUtilities.invokeLater(this::filter);
	}

	private void filter()
	{
		if (updating)
		{
			return;
		}
		String text = editorField.getText();
		List<String> matches = matching(all, text);
		updating = true;
		try
		{
			setModel(new DefaultComboBoxModel<>(matches.toArray(new String[0])));
			setSelectedItem(null);
			editorField.setText(text);
			if (isShowing() && editorField.hasFocus())
			{
				// Re-open so the popup resizes to the narrowed list.
				setPopupVisible(false);
				if (!matches.isEmpty())
				{
					setPopupVisible(true);
				}
			}
		}
		finally
		{
			updating = false;
		}
	}

	private void resetModel()
	{
		updating = true;
		try
		{
			setModel(new DefaultComboBoxModel<>(all.toArray(new String[0])));
			setSelectedItem(current);
			setText(current == null ? "" : current);
		}
		finally
		{
			updating = false;
		}
	}

	private void setText(String text)
	{
		boolean was = updating;
		updating = true;
		try
		{
			editorField.setText(text);
			// Show the start of long names ("Tombs of Amascut: ..."), not the tail.
			editorField.setCaretPosition(0);
		}
		finally
		{
			updating = was;
		}
	}

	/** Turns typed text into an entry: exact match first, then the first containing match. */
	private String resolve(String text)
	{
		if (text == null)
		{
			return null;
		}
		String trimmed = text.trim();
		if (trimmed.isEmpty())
		{
			return null;
		}
		for (String item : all)
		{
			if (item.equalsIgnoreCase(trimmed))
			{
				return item;
			}
		}
		List<String> matches = matching(all, trimmed);
		return matches.isEmpty() ? null : matches.get(0);
	}

	/** Entries containing the text, case-insensitive; the whole list for empty text. */
	static List<String> matching(List<String> items, String text)
	{
		String needle = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
		if (needle.isEmpty())
		{
			return new ArrayList<>(items);
		}
		List<String> starts = new ArrayList<>();
		List<String> contains = new ArrayList<>();
		for (String item : items)
		{
			String hay = item.toLowerCase(Locale.ROOT);
			if (hay.startsWith(needle))
			{
				starts.add(item);
			}
			else if (hay.contains(needle))
			{
				contains.add(item);
			}
		}
		starts.addAll(contains);
		return starts;
	}
}
