package jtrans.gui;

import jtrans.elements.Anchor;
import jtrans.elements.Element;
import jtrans.facade.Project;
import jtrans.facade.Track;
import jtrans.utils.spantable.DefaultSpanModel;
import jtrans.utils.spantable.Span;
import jtrans.utils.spantable.SpanModel;
import jtrans.utils.spantable.SpanTableModel;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;


class MultiTrackTableModel extends AbstractTableModel implements SpanTableModel {
	private Project project;
	private SpanModel spanModel = new DefaultSpanModel();
	private String[] columnNames;
	private Cell[][] cells;
	private int visibleColumns;


	class Cell {
		Anchor anchor;
		String text;

		public Cell(Anchor a, String t) {
			anchor = a;
			text = t;
		}

		@Override
		public String toString() {
			return "[" + anchor.seconds + "] " + text;
		}
	}


	@Override
	public int getRowCount() {
		return cells.length;
	}

	@Override
	public int getColumnCount() {
		return visibleColumns;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		return cells[rowIndex][columnIndex];
	}

	@Override
	public String getColumnName(int column) {
		return columnNames[column];
	}

	public MultiTrackTableModel(Project p, boolean[] visibility) {
		super();
		project = p;
		refresh(visibility);
	}


	@Override
	public boolean isCellEditable(int row, int column) {
		return false;
	}


	@Override
	public SpanModel getSpanModel() {
		return spanModel;
	}


	/**
	 * Keeps track of various variables when building table data for a Track.
	 */
	private class MetaTrack {
		final Track track;
		final int column;
		final ListIterator<Element> iter;
		Anchor anchor;
		int lastRow = 0;

		MetaTrack(int trackNo, int column) {
			this.column = column;
			track = project.tracks.get(trackNo);
			iter = track.elts.listIterator();

			// Skip past first anchor and add initial row span if needed
			ontoNextCell(0);
		}

		/**
		 * Adjusts lastRow and currentTime.
		 * Adds the current row span to the spanModel if needed.
		 * @return contents of the current cell
		 */
		void ontoNextCell(int currentRow) {
			int rowSpan = currentRow - lastRow;
			if (rowSpan > 1)
				spanModel.addSpan(new Span(lastRow, column, rowSpan, 1));
			lastRow = currentRow;

			Anchor cellStartAnchor = anchor;
			StringBuilder sb = new StringBuilder();

			// Invalidate currentTime
			if (!iter.hasNext())
				anchor = null;

			while (iter.hasNext()) {
				Element next = iter.next();
				if (next instanceof Anchor) {
					anchor = (Anchor)next;
					break;
				}
				sb.append(next.toString()).append(' ');
			}

			if (cells != null) {
				cells[currentRow][column] =
						new Cell(cellStartAnchor, sb.toString());
			}
		}
	}


	public void refresh(boolean[] visibility) {
		spanModel.clear();

		// Count visible tracks and initialize track metadata
		List<MetaTrack> metaTracks = new ArrayList<MetaTrack>();
		for (int i = 0; i < visibility.length; i++) {
			if (visibility[i])
				metaTracks.add(new MetaTrack(i, metaTracks.size()));
		}
		visibleColumns = metaTracks.size();

		columnNames = new String[visibleColumns];
		for (int i = 0; i < visibleColumns; i++)
			columnNames[i] = metaTracks.get(i).track.speakerName;

		// Count rows
		int rows = 0;
		for (MetaTrack mt: metaTracks)
			for (Element el: mt.track.elts)
				if (el instanceof Anchor)
					rows++;

		cells = new Cell[rows][visibleColumns];

		for (int row = 0; row < rows; row++) {
			MetaTrack meta = null;

			// Find track containing the earliest upcoming anchor
			for (MetaTrack m: metaTracks) {
				if (m.anchor != null &&
						(null == meta || m.anchor.seconds < meta.anchor.seconds))
				{
					meta = m;
				}
			}

			// No more anchors in all tracks
			if (null == meta)
				break;

			meta.ontoNextCell(row);
		}
	}
}
