package fr.loria.synalp.jtrans.gui.table;

import fr.loria.synalp.jtrans.project.Token;
import fr.loria.synalp.jtrans.project.TurnProject;
import fr.loria.synalp.jtrans.utils.spantable.Span;

public class TurnModel extends ProjectModel<TurnProject> {

	private int nonEmptyRowCount;
	private Object[][] cells;


	public TurnModel(TurnProject p) {
		project = p;

		int maxRowCount = 4 * project.turns.size();
		cells = new Object[project.speakerCount()][maxRowCount];

		for (int i = 0; i < project.speakerCount(); i++) {
			columns.add(new Column(i));
		}

		int row = 0;

		TurnProject.Turn previousTurn = null;

		for (int t = 0; t < project.turns.size(); t++) {
			assert (row+2) < maxRowCount;

			TurnProject.Turn turn = project.turns.get(t);

			if (turn.start != null &&
					(previousTurn == null || (previousTurn.end != null && !previousTurn.end.equals(turn.start))))
			{
				if (row > 0) {
					row++;
				}

				spanModel.addSpan(new Span(row, 0, 1, project.speakerCount()));
				cells[0][row++] = turn.start;
			}

			for (int spk = 0; spk < project.speakerCount(); spk++) {
				if (!turn.spkTokens.get(spk).isEmpty()) {
					cells[spk][row] = new TextCell(spk, turn.spkTokens.get(spk));
					for (Token token: turn.spkTokens.get(spk)) {
						columns.get(spk).tokenRowMap.put(token, row);
					}
				}
			}
			row++;

			if (turn.end != null) {
				spanModel.addSpan(new Span(row, 0, 1, project.speakerCount()));
				cells[0][row++] = turn.end;
			}

			previousTurn = turn;
		}

		nonEmptyRowCount = row;
	}


	@Override
	public int getRowCount() {
		return nonEmptyRowCount;
	}


	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		return cells[columnIndex][rowIndex];
	}

}
