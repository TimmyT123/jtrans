package jtrans.gui.trackview;

import jtrans.elements.Word;
import jtrans.facade.Project;
import jtrans.gui.JTransGUI;
import jtrans.gui.PlayerGUI;
import jtrans.utils.TimeConverter;
import jtrans.utils.spantable.SpanTable;

import java.awt.*;
import java.util.Arrays;
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

		renditor = new CellRenditor(this);

		refreshModel();
		setEnabled(true);
		setShowGrid(true);
		getTableHeader().setReorderingAllowed(false);
		//setIntercellSpacing(new Dimension(1, 1));
		setPreferredScrollableViewportSize(new Dimension(512, 512));
		setFillsViewportHeight(true);

		doLayout();
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


