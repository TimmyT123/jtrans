/*
This source code is copyrighted by Christophe Cerisara, CNRS, France.

It is licensed under the terms of the INRIA Cecill-C licence, as described in:
http://www.cecill.info/licences/Licence_CeCILL-C_V1-en.html
*/

package jtrans.gui;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;

import jtrans.speechreco.LiveSpeechReco;

public class ControlBox extends JPanel implements ActionListener {
	private JTransGUI aligneur;
	private PlayerGUI playergui;
	
	public PlayerGUI getPlayerGUI() {return playergui;}
	
	public ControlBox(JTransGUI main) {
		aligneur = main;
		playergui = new PlayerGUI(main);
		JButton asrButton = new JButton("AutoTranscript");
		asrButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				aligneur.asr();
			}
		});
		JButton liveButton = new JButton("Live ASR");
		liveButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JTransGUI.REIMPLEMENT_DEC2013(); /* TODO PARALLEL TRACKS
				LiveSpeechReco r = LiveSpeechReco.doReco(aligneur);
				r.addResultListener(new TextAreaRecoListener(aligneur.edit));
				*/
			}
		});
		JButton stopit = new JButton("Stop It !");
		stopit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// TODO: Stop auto-align
				LiveSpeechReco.stopall();
			}
		});
		
		Box b = Box.createHorizontalBox();
		add(b);
		b.add(Box.createGlue());
		b.add(playergui);
		b.add(Box.createGlue());
		b.add(asrButton);
		b.add(Box.createGlue());
		b.add(liveButton);
		b.add(Box.createGlue());
		b.add(stopit);
		b.add(Box.createGlue());
		
		playergui.addActionListener(this);
	}

	// cette methode est appelee depuis le PlayerGUI
	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getActionCommand().equals("playstart")) {
			playergui.setStartSec(aligneur.getCurPosInSec());
			aligneur.newplaystarted();
		} else if (e.getActionCommand().equals("rewind")) {
			aligneur.setCurPosInSec(0);
		} else if (e.getActionCommand().equals("playstop")) {
			aligneur.newplaystopped();
			float prevStartingTime = playergui.getRelativeStartingSec();
			aligneur.setCurPosInSec(prevStartingTime+(float)playergui.getTimePlayed()/1000f);
		}
	}
}
 