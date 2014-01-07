package jtrans.gui.trackview;

import jtrans.elements.Anchor;
import jtrans.elements.ElementList;
import jtrans.elements.Word;
import jtrans.facade.Project;
import jtrans.facade.Track;
import jtrans.gui.JTransGUI;
import jtrans.gui.PlayerGUI;
import jtrans.utils.TimeConverter;
import jtrans.utils.spantable.SpanTable;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.table.*;


/**
 * Represents tracks as columns.
 * Renders groups of words between two anchors as a JTextArea cell.
 */
public class MultiTrackTable extends SpanTable {
	private Project project;
	private JTransGUI gui; // used in UI callbacks
	private MultiTrackTableModel model;
	private CellRenditor renditor;
	private boolean[] visibility;
	private int visibleCount;


	public MultiTrackTable(Project project, JTransGUI gui) {
		this.project = project;
		this.gui = gui;

		visibility = new boolean[project.tracks.size()];
		Arrays.fill(visibility, true);
		visibleCount = visibility.length;

		renditor = new CellRenditor();

		refreshModel();
		setEnabled(true);
		setShowGrid(true);
		getTableHeader().setReorderingAllowed(false);
		//setIntercellSpacing(new Dimension(1, 1));
		setPreferredScrollableViewportSize(new Dimension(512, 512));
		setFillsViewportHeight(true);

		addMouseListener(new MultiTrackTableMouseAdapter());
		putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

		doLayout();
	}


	class MultiTrackTableMouseAdapter extends MouseAdapter {
		@Override
		public void mousePressed(MouseEvent e) {
			int row = rowAtPoint(e.getPoint());
			int col = columnAtPoint(e.getPoint());

			Cell cell = model.getValueAt(row, col);
			if (cell == null)
				return;

			// Convert click to cell coordinate system
			Rectangle cprect = getCellRect(row, col, true);
			Point p = e.getPoint();
			p.translate(-cprect.x, -cprect.y);

			// Start editing: necessary to get proper layout in editor pane
			// (especially when right-clicking)
			editCellAt(row, col);
			CellPane pane = (CellPane)prepareEditor(renditor, row, col);

			Word word = cell.getWordAtCaret(pane.viewToModel(p));

			// Stop editing now, otherwise future mouse events will be
			// consumed by the editor and won't be seen by the table
			renditor.stopCellEditing();

			if (e.isPopupTrigger()) {
				wordPopupMenu(project.tracks.get(cell.track), word, e);
			} else if (word != null) {
				selectWord(cell.track, word);
			}
		}
	}


	/**
	 * Refreshes the MultiTrackTableModel. Should be called after a column is
	 * hidden or shown.
	 */
	private void refreshModel() {
		model = new MultiTrackTableModel(project, visibility);
		setModel(model);
	}


	/**
	 * Sets our custom cell renderer for all cells.
	 */
	@Override
	public void setModel(TableModel tm) {
		super.setModel(tm);
		for (int i = 0; i < getColumnModel().getColumnCount(); i++) {
			getColumnModel().getColumn(i).setCellRenderer(renditor);
			getColumnModel().getColumn(i).setCellEditor(renditor);
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
			for (int i = 0; i < project.tracks.size(); i++) {
				if (!visibility[i])
					continue;

				TableColumn tableCol = tcm.getColumn(col);
				Component cell = prepareRenderer(tableCol.getCellRenderer(),
						row, col);

				cell.setSize(
						tableCol.getWidth() - getIntercellSpacing().width,
						cell.getPreferredSize().height);

				int h = cell.getPreferredSize().height + getIntercellSpacing().height;
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
		renditor.setFont(font);
		doLayout();
	}


	public void setTrackVisible(int index, boolean v) {
		assert index >= 0 && index < project.tracks.size();

		if (v && !visibility[index]) {
			visibleCount++;
		} else if (!v && visibility[index]) {
			visibleCount--;
		} else {
			return;
		}

		visibility[index] = v;
		refreshModel();
		doLayout();
	}


	public int getVisibleCount() {
		return visibleCount;
	}


	public void highlightWord(int trackIdx, Word word) {
		model.highlightWord(trackIdx, word);
	}


	private void wordPopupMenu(final Track track, final Word word, MouseEvent event) {
		JPopupMenu popup = new JPopupMenu("Word/Anchor");

		if (word != null) {
			popup.add(new JMenuItem(String.format("New anchor before '%s'",
					word.getWordString()))
			{{
					addActionListener(new AbstractAction() {
						@Override
						public void actionPerformed(ActionEvent e) {
							newAnchorNextToWord(track, word, true);
						}
					});
				}});

			popup.add(new JMenuItem(String.format("New anchor after '%s'",
					word.getWordString()))
			{{
					addActionListener(new AbstractAction() {
						@Override
						public void actionPerformed(ActionEvent e) {
							newAnchorNextToWord(track, word, false);
						}
					});
			}});

			popup.addSeparator();
		}

		popup.add("Test");
		popup.add("Test");
		popup.add("Test");

		popup.show(this, event.getX(), event.getY());
	}


	/**
	 * Dialog box to create an anchor before or after a certain word.
	 * @param before If true, the new anchor will be placed before the word in
	 *               the element list. If false, it'll be placed after the word.
	 */
	private void newAnchorNextToWord(Track track, Word word, boolean before) {
		ElementList.Neighborhood<Anchor> range =
				track.elts.getNeighbors(word, Anchor.class);

		float initialPos;

		if (word.posInAlign < 0) {
			float endOfAudio = TimeConverter.frame2sec((int) project.audioSourceTotalFrames);
			initialPos = before?
					(range.prev!=null? range.prev.seconds: 0) :
					(range.next!=null? range.next.seconds: endOfAudio);
		} else if (before) {
			initialPos = TimeConverter.frame2sec(
					track.words.getSegmentDebFrame(word.posInAlign));
		} else {
			initialPos = TimeConverter.frame2sec(
					track.words.getSegmentEndFrame(word.posInAlign));
		}

		String positionString = JOptionPane.showInputDialog(gui.jf,
				String.format("Enter position for new anchor to be inserted\n"
						+ "%s '%s' (in seconds):",
						before? "before": "after", word.getWordString()),
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


	/**
	 * Ensures newPos is a valid position for an anchor within the given
	 * range; if not, informs the user with error messages.
	 * @return true if the position is valid
	 */
	private boolean sanitizeAnchorPosition(
			ElementList.Neighborhood<Anchor> range, float newPos)
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


	/**
	 * Highlights a word and sets the playback position to the beginning of the
	 * word.
	 */
	public void selectWord(int trackIdx, Word word) {
		PlayerGUI player = gui.ctrlbox.getPlayerGUI();
		boolean replay = player.isPlaying();
		player.stopPlaying();

		if (word.posInAlign >= 0) {
			model.highlightWord(trackIdx, word);
			gui.setCurPosInSec(TimeConverter.frame2sec(
					project.tracks.get(trackIdx).words.getSegmentDebFrame(word.posInAlign)));
		} else {
			replay = false;
		}

		if (replay)
			player.startPlaying();
	}
}


