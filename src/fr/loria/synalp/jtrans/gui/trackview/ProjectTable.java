package fr.loria.synalp.jtrans.gui.trackview;

import fr.loria.synalp.jtrans.gui.*;
import fr.loria.synalp.jtrans.project.*;
import fr.loria.synalp.jtrans.utils.*;
import fr.loria.synalp.jtrans.utils.spantable.SpanTable;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.table.*;

/* Implementation notes: JTable's standard "editing" mode doesn't play nice with
MultiTrackTable's heavily customized view. Mouse events worked haphazardly.
We decided to completely eschew cell editors in favor of panels acting as both
renderers and "editors". */

/**
 * Represents tracks as columns.
 * Renders groups of words between two anchors as a JTextArea cell.
 */
public class ProjectTable
		extends SpanTable
		implements TableCellRenderer
{
	public static final Font DEFAULT_FONT =
			new Font(Font.SANS_SERIF, Font.PLAIN, 13);

	private Project project;
	private JTransGUI gui; // used in UI callbacks
	private ProjectModel model;

	// Cell rendering attributes
	private JPanel silenceComp;
	private JLabel anchorComp;
	private CellPane textComp;


	/**
	 * @param gui used in UI callbacks. May be null ONLY if interactive is null!
	 * @param interactive whether to react to mouse events
	 */
	public ProjectTable(Project project, JTransGUI gui, boolean interactive) {
		this.project = project;
		this.gui = gui;

		refreshModel();
		setEnabled(interactive);
		setShowGrid(true);
		setIntercellSpacing(new Dimension(1, 0));
		getTableHeader().setReorderingAllowed(false);
		setPreferredScrollableViewportSize(new Dimension(900, 400));
		setFillsViewportHeight(true);

		if (interactive) {
			addMouseListener(new MultiTrackTableMouseAdapter());
			putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
		}

		setBackground(Color.DARK_GRAY);
		setGridColor(getBackground());

		//----------------------------------------------------------------------
		// Cell rendering panes

		textComp = new CellPane();

		silenceComp = new JPanel();
		silenceComp.setBackground(getBackground());

		anchorComp = new JLabel("ANCHOR");
		anchorComp.setOpaque(true);
		anchorComp.setBackground(Color.LIGHT_GRAY);
		anchorComp.setForeground(Color.DARK_GRAY);
		anchorComp.setHorizontalAlignment(SwingConstants.CENTER);

		//----------------------------------------------------------------------

		setViewFont(DEFAULT_FONT); // also calls doLayout()
	}


	@Override
	public Component getTableCellRendererComponent(
			JTable table, Object value,
			boolean isSelected, boolean hasFocus,
			int row, int column)
	{
		if (value instanceof TextCell) {
			textComp.setCell((TextCell) value);

			ProjectModel model = (ProjectModel)table.getModel();
			if (model.getHighlightedRow(column) == row) {
				textComp.highlight(model.getHighlightedToken(column));
			}

			return textComp;
		}

		else if (value instanceof Anchor) {
			anchorComp.setText(value.toString());
			return anchorComp;
		}

		else {
			return silenceComp;
		}
	}


	class MultiTrackTableMouseAdapter extends MouseAdapter {
		@Override
		public void mousePressed(MouseEvent e) {
			boolean isPopupTrigger = CrossPlatformFixes.isPopupTrigger(e);

			int row = rowAtPoint(e.getPoint());
			int col = columnAtPoint(e.getPoint());
			JPopupMenu popup = null;

			if (row < 0 || col < 0)
				return;

			Object cell = model.getValueAt(row, col);
			if (cell == null)
				return;

			// Convert click to cell coordinate system
			Rectangle cprect = getCellRect(row, col, true);
			Point p = e.getPoint();
			p.translate(-cprect.x, -cprect.y);

			if (cell instanceof TextCell) {
				CellPane pane = (CellPane)prepareRenderer(ProjectTable.this, row, col);
				pane.setSize(getColumnModel().getColumn(col).getWidth(), getRowHeight(row));

				TextCell textCell = (TextCell)cell;
				Token token = textCell.getElementAtCaret(pane.viewToModel(p));

				if (isPopupTrigger) {
//					popup = wordPopupMenu(textCell.anchor, col, word);
					JOptionPane.showMessageDialog(null, "Reimplement me");
				} else if (token.isAlignable()) {
					selectWord(textCell.spkID, token);
				}
			}

			else if (cell instanceof Anchor) {
//				if (isPopupTrigger)
//					popup = anchorPopupMenu((Anchor)cell, col);
				JOptionPane.showMessageDialog(null, "Reimplement me");
			}

			if (popup != null)
				popup.show(ProjectTable.this, e.getX(), e.getY());

		}
	}


	/**
	 * Refreshes the MultiTrackTableModel. Should be called after a column is
	 * hidden or shown.
	 */
	public void refreshModel() {
		if (project instanceof TurnProject) {
			model = new TurnModel((TurnProject)project);
		} else if (project instanceof TrackProject) {
			model = new TrackModel((TrackProject)project);
		} else {
			throw new IllegalArgumentException("no table model available " +
					"for project class " + project.getClass());
		}
		setModel(model);
	}


	/**
	 * Sets our custom cell renderer for all cells.
	 */
	@Override
	public void setModel(TableModel tm) {
		super.setModel(tm);
		for (int i = 0; i < getColumnModel().getColumnCount(); i++) {
			getColumnModel().getColumn(i).setCellRenderer(this);
		}
	}


	/**
	 * TextArea cells may wrap lines. Row height is flexible.
	 */
	@Override
	public void doLayout() {
		System.out.println("Do Layout");

		TableColumnModel tcm = getColumnModel();

		for (int row = 0; row < getRowCount(); row++) {
			int newRowHeight = 1;
			int col = 0;
			for (int i = 0; i < project.speakerCount(); i++) {
				TableColumn tableCol = tcm.getColumn(col);
				Component cell = prepareRenderer(tableCol.getCellRenderer(),
						row, col);

				int prefHeight = cell.getPreferredSize().height;
				if (prefHeight < 16) {
					prefHeight = 16;
				}

				cell.setSize(
						tableCol.getWidth() - getIntercellSpacing().width,
						prefHeight);

				int h = prefHeight + getIntercellSpacing().height;
				if (h > newRowHeight)
					newRowHeight = h;

				col++;
			}
			if (getRowHeight(row) != newRowHeight)
				setRowHeight(row, newRowHeight);
		}
		super.doLayout();
	}


	public void setViewFont(Font font) {
		textComp.setFont(font);
		doLayout();
	}


	public void highlightWord(int trackIdx, Token token) {
		model.highlightToken(trackIdx, token);

		if (token != null) {
			scrollRectToVisible(getCellRect(
					model.getHighlightedRow(trackIdx), trackIdx, true));
		}
	}



	/*
	private JPopupMenu anchorPopupMenu(final Anchor anchor, final Track track) {
		JPopupMenu popup = new JPopupMenu("Anchor");

		popup.add(new JMenuItem("Adjust anchor time (" + anchor.seconds + ")") {{
			addActionListener(new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent e) {
					repositionAnchor(anchor, track);
				}
			});
		}});

		popup.add(new JMenuItem("Delete anchor") {{
			addActionListener(new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent e) {
					track.clearAlignmentAround(anchor);
					track.elts.remove(anchor);
					refreshModel();
				}
			});
		}});

		return popup;
	}


	private JPopupMenu wordPopupMenu(final Anchor anchor, final Track track, final Word word) {
		JPopupMenu popup = new JPopupMenu("Word");

		String wordString = word == null?
				"<no word selected>": String.format("'%s'", word);

		popup.add(new JMenuItem("New anchor before " + wordString) {{
			setEnabled(word != null);
			addActionListener(new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent e) {
					newAnchorNextToWord(track, word, true);
				}
			});
		}});

		popup.add(new JMenuItem("New anchor after " + wordString) {{
			setEnabled(word != null);
			addActionListener(new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent e) {
					newAnchorNextToWord(track, word, false);
				}
			});
		}});

		popup.add(new JMenuItem("Clear alignment in this cell") {{
			addActionListener(new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent e) {
					Track.Neighborhood<Anchor> ancRange =
							track.getNeighbors(anchor, Anchor.class);
					track.clearAlignmentBetween(anchor, ancRange.next);
					ProjectTable.this.repaint();
				}
			});
		}});

		return popup;
	}
	*/


	/**
	 * Dialog box to prompt the user where to reposition the anchor.
	 * User input is sanitized with sanitizeAnchorPosition().
	 */
	/*
	private void repositionAnchor(Anchor anchor, Track track) {
		String newPosString = JOptionPane.showInputDialog(gui.jf,
				"Enter new anchor position in seconds:",
				Float.toString(anchor.seconds));

		if (newPosString == null)
			return;

		float newPos = Float.parseFloat(newPosString);

		if (!sanitizeAnchorPosition(
				track.getNeighbors(anchor, Anchor.class), newPos))
		{
			return;
		}

		anchor.seconds = newPos;

		track.clearAlignmentAround(anchor);

		refreshModel();
	}
	*/


	/**
	 * Dialog box to create an anchor before or after a certain word.
	 * @param before If true, the new anchor will be placed before the word in
	 *               the element list. If false, it'll be placed after the word.
	 */
	/*
	private void newAnchorNextToWord(Track track, Word word, boolean before) {
		Track.Neighborhood<Anchor> range =
				track.getNeighbors(word, Anchor.class);

		float initialPos;

		if (!word.isAligned()) {
			float endOfAudio = TimeConverter.frame2sec((int) project.audioSourceTotalFrames);
			initialPos = before?
					(range.prev!=null? range.prev.seconds: 0) :
					(range.next!=null? range.next.seconds: endOfAudio);
		} else if (before) {
			initialPos = word.getSegment().getStartSecond();
		} else {
			initialPos = word.getSegment().getEndSecond();
		}

		String positionString = JOptionPane.showInputDialog(gui.jf,
				String.format("Enter position for new anchor to be inserted\n"
						+ "%s '%s' (in seconds):",
						before? "before": "after", word),
				initialPos);

		if (positionString == null)
			return;

		float newPos = Float.parseFloat(positionString);

		if (sanitizeAnchorPosition(range, newPos)) {
			Anchor anchor = new Anchor(newPos);
			track.elts.add(track.elts.indexOf(word) + (before?0:1), anchor);
			track.clearAlignmentAround(anchor);
			refreshModel(); //setTextFromElements();
		}
	}
	*/


	/**
	 * Ensures newPos is a valid position for an anchor within the given
	 * range; if not, informs the user with error messages.
	 * @return true if the position is valid
	 */
	/*
	private boolean sanitizeAnchorPosition(
			Track.Neighborhood<Anchor> range, float newPos)
	{
		if (newPos < 0) {
			JOptionPane.showMessageDialog(gui.jf,
					"Can't set to negative position!",
					"Illegal anchor position", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		if (range.prev != null && range.prev.seconds > newPos) {
			JOptionPane.showMessageDialog(gui.jf,
					"Can't set this anchor before the previous anchor\n" +
							"(at " + range.prev.seconds + " seconds).",
					"Illegal anchor position", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		if (range.next != null && range.next.seconds < newPos) {
			JOptionPane.showMessageDialog(gui.jf,
					"Can't set this anchor past the next anchor\n" +
							"(at " + range.next.seconds + " seconds).",
					"Illegal anchor position", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		return true;
	}

	*/

	/**
	 * Highlights a word and sets the playback position to the beginning of the
	 * word.
	 */
	public void selectWord(int spkID, Token word) {
		PlayerGUI player = gui.ctrlbox.getPlayerGUI();
		boolean replay = player.isPlaying();
		player.stopPlaying();

		if (word.isAligned()) {
			model.highlightToken(spkID, word);
			gui.setCurPosInSec(word.getSegment().getStartSecond());
			gui.sigpan.setSpeaker(spkID);
		} else {
			replay = false;
		}

		if (replay)
			player.startPlaying();
	}
}


