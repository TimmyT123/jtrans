/*
This source code is copyrighted by Christophe Cerisara, CNRS, France.

It is licensed under the terms of the INRIA Cecill-C licence, as described in:
http://www.cecill.info/licences/Licence_CeCILL-C_V1-en.html
 */

package plugins.applis.SimpleAligneur;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.LinkedHashMap;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import edu.cmu.sphinx.result.Result;

import main.JTrans;
import main.LiveSpeechReco;

import plugins.sourceSignals.Mike2wav;
import plugins.speechreco.RecoListener;
import plugins.speechreco.adaptation.BiaisAdapt;
import plugins.speechreco.aligners.sphiinx4.AlignementEtat;
import plugins.speechreco.aligners.sphiinx4.S4ForceAlignBlocViterbi;
import plugins.speechreco.grammaire.Grammatiseur;
import plugins.utils.TextInputWindow;
import plugins.utils.UserInputProcessor;

public class Menus {
	Aligneur aligneur;

	String reco;
	boolean[] done = {false};
	LiveSpeechReco gram;

	public Menus(Aligneur main) {
		aligneur=main;
	}
	public JMenuBar menus() {
		final JMenuBar menubar = new JMenuBar();

		// //////////////////////////////////////////////////////////////
		JMenu file = new JMenu("File");

		JMenuItem loadwav = new JMenuItem("Load .WAV...");
		JMenuItem loadtxt = new JMenuItem("Load .TXT...");
		JMenuItem loadjtr = new JMenuItem("Load JTrans project...");
		JMenuItem loadtrs = new JMenuItem("Load ref TRS...");
		JMenuItem savewav = new JMenuItem("Save .WAV as...");
		JMenuItem savetxt = new JMenuItem("Save .TXT as...");
		JMenuItem savejtr = new JMenuItem("Save JTrans project as...");
		JMenuItem savepho = new JMenuItem("Save align.pho");
		JMenuItem savepraat = new JMenuItem("Save as Praat text grid...");
		JMenuItem saveParallelPraat = new JMenuItem("Save as Praat parallel tiers (experimental)...");
		JMenuItem quit = new JMenuItem("Quit");

		menubar.add(file);
		file.add(loadwav);
		file.add(loadtxt);
		file.add(loadjtr);
		file.add(loadtrs);
		file.addSeparator();
		file.add(savewav);
		file.add(savetxt);
		file.add(savejtr);
		file.add(savepho);
		file.add(savepraat);
		file.add(saveParallelPraat);
		file.addSeparator();
		file.add(quit);

		savetxt.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser filechooser = new JFileChooser();
				filechooser.setDialogTitle("Save .TXT...");
				filechooser.setSelectedFile(new File("out.txt"));
				int returnVal = filechooser.showSaveDialog(aligneur.jf);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File file = filechooser.getSelectedFile();
					aligneur.savetxt(file);
				}
			}
		});

		loadtrs.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				JFileChooser filechooser = new JFileChooser();
				filechooser.setDialogTitle("Load TRS...");
				int returnVal = filechooser.showOpenDialog(aligneur.jf);
				if (returnVal == filechooser.APPROVE_OPTION) {
					File file = filechooser.getSelectedFile();
					aligneur.loadTRSWithProgress(file.getAbsolutePath());
				}
			}
		});
		//		JMenuItem loadstm = new JMenuItem("load STM");
		//		file.add(loadstm);
		//		JMenuItem savetrs = new JMenuItem("save TRS");
		//		file.add(savetrs);

		savepho.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				//				aligneur.alignement.savePho("align.pho");
				System.out.println("saving: "+(new File("align.pho")).getAbsolutePath());
			}
		});

		savepraat.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JPanel choicePane = new JPanel();
				choicePane.setLayout(new GridLayout(3, 1));

				JCheckBox jcbPhonemes = new JCheckBox("Phonemes", true);
				JCheckBox jcbWords = new JCheckBox("Words", true);
				JCheckBox jcbSpeakers = new JCheckBox("Speakers", true);

				choicePane.add(jcbPhonemes);
				choicePane.add(jcbWords);
				choicePane.add(jcbSpeakers);

				int rc = JOptionPane.showConfirmDialog(aligneur.jf, choicePane,
						"Select tiers to export",
						JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

				if (rc != JOptionPane.OK_OPTION)
					return;

				LinkedHashMap<String, AlignementEtat> tiers = new LinkedHashMap<String, AlignementEtat>();
				if (jcbPhonemes.isSelected())
					tiers.put("Phonemes", aligneur.alignementPhones);
				if (jcbWords.isSelected())
					tiers.put("Words", aligneur.alignement);
				if (jcbSpeakers.isSelected())
					tiers.put("Speakers", aligneur.generateSpeakerAlignment());

				if (tiers.isEmpty())
					return;

				JFileChooser fc = new JFileChooser();
				fc.setDialogTitle("Save to Praat text grid...");
				fc.setSelectedFile(new File("out.textGrid"));
				int returnVal = fc.showSaveDialog(aligneur.jf);

				if (returnVal == fc.APPROVE_OPTION) {
					File file = fc.getSelectedFile();
					JTrans.savePraat(file.getAbsolutePath(), tiers);
				}
			}
		});

		saveParallelPraat.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser fc = new JFileChooser();
				fc.setDialogTitle("Save to Praat text grid...");
				fc.setSelectedFile(new File("out.textGrid"));
				int returnVal = fc.showSaveDialog(aligneur.jf);

				if (returnVal == fc.APPROVE_OPTION) {
					String praat = aligneur.generatePraatWithTiersBySpeaker();
					File file = fc.getSelectedFile();
					try {
						FileWriter fw = new FileWriter(file);
						fw.write(praat);
						fw.flush();
						fw.close();
					} catch (IOException ex) {
					}
				}
			}
		});

		// //////////////////////////////////////////////////////////////
		JMenu actionsm = new JMenu("Edit");
		JMenuItem parsestd = new JMenuItem("Parse text standard");
		JMenuItem parse = new JMenuItem("Parse text regexp");
		JMenuItem editb = new JMenuItem("Edit text");
		JMenuItem regexp = new JMenuItem("Regexps");
		JMenuItem gototime = new JMenuItem("Go to time [sec]");
		menubar.add(actionsm);
		actionsm.add(parsestd);
		actionsm.add(parse);
		actionsm.add(editb);
		actionsm.add(regexp);
		actionsm.add(gototime);

		// //////////////////////////////////////////////////////////////
		JMenu sig = new JMenu("Process");
		JMenuItem bias = new JMenuItem("Bias adapt");
		JMenuItem map = new JMenuItem("MAP adapt");
		JMenuItem mapload = new JMenuItem("Load adapted models");
		JMenuItem clear = new JMenuItem("Clear all align");
		JMenuItem clearfrom = new JMenuItem("Clear align from selected word");
		JMenuItem asr= new JMenuItem("Automatic Speech Recognition");
		JMenuItem asrJSAPI= new JMenuItem("JSAPI Speech Recognition");
		JMenuItem batch= new JMenuItem("Batch align all");
		JMenuItem beam= new JMenuItem("Set beam");
		JMenuItem playfrom = new JMenuItem("Play from");

		menubar.add(sig);

		sig.add(bias);
		sig.add(map);
		sig.add(mapload);
		sig.add(clear);
		sig.add(clearfrom);
		sig.add(asr);
		sig.add(asrJSAPI);
		sig.add(batch);
		sig.add(beam);
		sig.addSeparator();
		sig.add(playfrom);

		bias.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				aligneur.biasAdapt();
			}
		});

		map.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				BiaisAdapt a = new BiaisAdapt(aligneur);
				a.adaptMAP(aligneur.alignementPhones);
			}
		});

		mapload.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				BiaisAdapt a = new BiaisAdapt(aligneur);
				a.loadAdapted(null);
			}
		});


		//		JMenuItem batchalign= new JMenuItem("batch align between anchors");
		//		actionsm.add(batchalign);


		beam.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String s = JOptionPane.showInputDialog("Beam value (0=no beam)");
				if (s==null) return;
				s=s.trim();
				S4ForceAlignBlocViterbi.beamwidth=Integer.parseInt(s);
			}
		});

		playfrom.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				TextInputWindow tt = new TextInputWindow("position en secondes:",new UserInputProcessor() {
					public void processInput(String ii) {
						float nsec = Float.parseFloat(ii);
						aligneur.startPlayingFrom(nsec);
					}
				});
			}
		});

		// //////////////////////////////////////////////////////////////
		JMenu prefs = new JMenu("Options");
		JMenuItem mixers = new JMenuItem("Audio mixers");
		JMenuItem mikerec = new JMenuItem("Record from mic");
		JMenuItem liveasr = new JMenuItem("Live ASR");
		JMenuItem gui3 = new JMenuItem("GUI: toggle words/phones");
		JMenuItem font = new JMenuItem("Font size");
		menubar.add(prefs);
		//		JMenuItem mots = new JMenuItem("forward mots");
		//		prefs.add(mots);
		prefs.add(mixers);
		prefs.add(mikerec);
		prefs.add(liveasr);
		prefs.add(gui3);
		prefs.add(font);

		liveasr.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// asynchrone
				LiveSpeechReco.doReco();
				// TODO: wait for the user press the ESC key, then stop the reco and put the result in the text panel 
			}
		});

		//		JMenuItem gui1 = new JMenuItem("GUI: view text only");
		//		prefs.add(gui1);
		//		JMenuItem gui2 = new JMenuItem("GUI: view signal");
		//		prefs.add(gui2);

		//		gui1.addActionListener(new ActionListener() {
		//			public void actionPerformed(ActionEvent e) {
		//				aligneur.GUIfast();
		//			}
		//		});
		//		gui2.addActionListener(new ActionListener() {
		//			public void actionPerformed(ActionEvent e) {
		//				aligneur.GUIslow();
		//			}
		//		});
		gui3.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				aligneur.toggleShowPhones();
				//				TemporalSigPanel.showPhones=!TemporalSigPanel.showPhones;
				//				aligneur.repaint();
			}
		});
		font.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				final String[] fonts = {"10","12","16","18","20","24","30"};

				final JFrame fl = new JFrame("choose font size");
				final JList jl = new JList(fonts);
				jl.addListSelectionListener(new ListSelectionListener() {
					public void valueChanged(ListSelectionEvent e) {
						aligneur.edit.fontSize(Integer.parseInt((String)jl.getSelectedValue()));
						fl.dispose();
					}
				});
				fl.getContentPane().add(jl);
				fl.setSize(100, 100);
				fl.setVisible(true);
			}
		});

		// //////////////////////////////////////////////////////////////
		JMenu help = new JMenu("Help");
		menubar.add(help);
		JMenuItem tuto = new JMenuItem("Tutorial");
		help.add(tuto);
		tuto.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {

				try {
					JEditorPane pane = new JEditorPane((new File("tutorial.html")).toURI().toURL());
					pane.setEditable(false);
					pane.setSize(800, 800);
					JFrame helpframe = new JFrame("Help JTrans");
					JScrollPane hh = new JScrollPane(pane);
					helpframe.getContentPane().add(hh);
					helpframe.setSize(600, 800);
					helpframe.setVisible(true);
				} catch (MalformedURLException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				//				HTMLEditorKit helpwin = new HTMLEditorKit();
				//				helpwin.in
				//				View v = helpwin.getViewFactory().create(helpwin.createDefaultDocument().getDefaultRootElement());
			}
		});

		// //////////////////////////////////////////////////////////////
		// //////////////////////////////////////////////////////////////


		mixers.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Mixer.Info[] mix = AudioSystem.getMixerInfo();
				for (int i=0;i<mix.length;i++)
					System.out.println("mixer "+i+" "+mix[i]);
				JMenu mixmen = new JMenu("mixers");
				menubar.add(mixmen);
				JMenuItem[] mi = new JMenuItem[mix.length];
				for (int i=0;i<mix.length;i++) {
					final int j = i;
					mi[i] = new JMenuItem(mix[i].toString());
					mixmen.add(mi[i]);
					mi[i].addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							aligneur.mixidx=j;
						}
					});
				}
				menubar.validate();
				menubar.repaint();
			}
		});
		mikerec.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int mixidx = aligneur.mixidx;
				Mike2wav mike2sav = Mike2wav.getMike2wav(mixidx);
				mike2sav.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						String wavnom = e.getActionCommand().split(" ")[1];
						System.out.println("wavsource for mike : "+wavnom);
						aligneur.setAudioSource(wavnom);
						aligneur.setCurPosInSec(0);
						aligneur.repaint();
					}
				});
			}
		});

		//		mots.addActionListener(new ActionListener() {
		//			public void actionPerformed(ActionEvent e) {
		//				new TextInputWindow("enter nb of words for auto. align",new UserInputProcessor() {
		//					public void processInput(String txt) {
		//						aligneur.setNwordsForward(Integer.parseInt(txt));
		//					}
		//				});
		//			}
		//		});
		//		
		regexp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new plugins.text.regexp.graphique.RegExpFrame(aligneur.edit);
			}
		});
		parse.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				aligneur.parse(false);
			}
		});
		parsestd.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				aligneur.parse(true);
			}
		});
		gototime.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				aligneur.gototime();
			}
		});
		clear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				aligneur.clearAlign();
				aligneur.repaint();
			}
		});
		clearfrom.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				aligneur.clearAlignFrom(aligneur.wordSelectedIdx);
			}
		});
		editb.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				aligneur.setEditionMode();
			}
		});

		//		batchalign.addActionListener(new ActionListener() {
		//			public void actionPerformed(ActionEvent e) {
		//				aligneur.batchAlign();
		//			}
		//		});

		batch.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				aligneur.batch();
			}
		});

		asr.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				aligneur.asr();
			}
		});
		asrJSAPI.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					File vocfile = new File("res/voc.txt");
					Grammatiseur.fastLoading=true;
					Grammatiseur.grammatiseur=null;
					gram = new LiveSpeechReco();
					gram.wavfile = aligneur.convertedAudioFile.getAbsolutePath();
					gram.loadVoc(vocfile);
					gram.initGrammar();
					gram.gram=gram;
					gram.addResultListener(new RecoListener() {
						@Override
						public void recoFinie(Result finalres, String res) {
							System.out.println("reco fin "+Thread.currentThread().getName()+" "+res);
							reco=res;
							synchronized (done) {
								done[0]=true;
								done.notify();
							}
						}
						@Override
						public void recoEnCours(Result tmpres) {
							System.out.println("reco en cours"+tmpres);
						}
					});
					Thread recothread = new Thread(new Runnable() {
						@Override
						public void run() {
							gram.wavReco();
							// au cas ou la reco s'arrete sans terminer completement
							synchronized (done) {
								done[0]=true;
								done.notify();
							}
						}
					});
					recothread.start();
					for (;;) {
						synchronized (done) {
							System.out.println("thread waiting: "+Thread.currentThread().getName());
							done.wait();
							System.out.println("done waiting: "+Thread.currentThread().getName());
							if (done[0]) break;
						}
					}
					String[] ss = reco.split("\n");
					StringBuilder res = new StringBuilder();
					for (int i=0;i<ss.length;i++) {
						String[] x = ss[i].split(":");
						if (x.length==3 && !x[2].equals("SIL")) {
							res.append(x[2]+" ");
						}
					}
					aligneur.edit.setText(res.toString());
					aligneur.edit.repaint();
				} catch (Exception ee) {
					ee.printStackTrace();
				}
			}
		});

		aligneur.playerController = new PlayerListener(aligneur, 100);


		savejtr.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				aligneur.saveProject();
			}
		});
		loadjtr.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				aligneur.loadProject();
			}
		});
		//		loadstm.addActionListener(new ActionListener() {
		//			public void actionPerformed(ActionEvent e) {
		//				JFileChooser filechooser = new JFileChooser();
		//				filechooser.validate();
		//				filechooser.setApproveButtonText("Ouvrir");
		//				int returnVal = filechooser.showOpenDialog(null);
		//				if (returnVal == JFileChooser.APPROVE_OPTION) {
		//					File file = filechooser.getSelectedFile();
		//					if (file.exists()) {
		////						aligneur.loadSTM reference(file);
		//					}
		//				}
		//			}
		//		});
		//		savetrs.addActionListener(new ActionListener() {
		//			public void actionPerformed(ActionEvent e) {
		//				JFileChooser filechooser = new JFileChooser();
		//				filechooser.validate();
		//				filechooser.setApproveButtonText("Ouvrir");
		//				int returnVal = filechooser.showOpenDialog(null);
		//				if (returnVal == JFileChooser.APPROVE_OPTION) {
		//					File file = filechooser.getSelectedFile();
		//					// TODO
		//				}
		//			}
		//		});
		quit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				aligneur.quit();
			}
		});
		loadwav.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser filechooser = new JFileChooser();
				filechooser.setDialogTitle("Load .WAV...");
				int returnVal = filechooser.showOpenDialog(null);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File file = filechooser.getSelectedFile();
					if (file.exists()) {
						aligneur.setAudioSource(file.getAbsolutePath());
						aligneur.setCurPosInSec(0);
						return;
					}
				}
				System.out.println("load wav pb");
			}
		});
		savewav.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser filechooser = new JFileChooser();
				filechooser.setDialogTitle("Save .WAV...");
				filechooser.setSelectedFile(new File("out.wav"));
				int returnVal = filechooser.showSaveDialog(aligneur.jf);
				if (returnVal != JFileChooser.APPROVE_OPTION)
					return;

				File file = filechooser.getSelectedFile();
				try {
					aligneur.saveWave(file);
				} catch (IOException ex) {
					ex.printStackTrace();
					JOptionPane.showMessageDialog(aligneur.jf,
							"I/O error when saving WAVE file",
							"Error",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		});
		loadtxt.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser filechooser = new JFileChooser();
				int returnVal = filechooser.showOpenDialog(aligneur.jf);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File file = filechooser.getSelectedFile();
					if (file.exists()) {
						aligneur.loadtxt(file);
					}
				}
			}
		});
		return menubar;
	}


}
