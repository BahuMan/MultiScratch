package multiscratch;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class ScratchMPView extends JFrame implements ActionListener, ScratchCommandListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		ScratchMPView srv = new ScratchMPView();
		srv.setVisible(true);
	}
	
	private ScratchMPModel scratchMultiPlayer;
	
	private JTextArea log;
	private JTextField ipField;
	private JButton connectButton;
	private JButton clearButton;
	private JButton exitButton;
	
	public ScratchMPView() {
		log = new JTextArea("This field will contain the logfile");
		log.setAutoscrolls(true);
		add(log, BorderLayout.CENTER);
		
		//construct a bar of buttons to be attached at the bottom of the frame:
		JPanel bar = new JPanel(new GridLayout(1, 0));
		
		ipField = new JTextField("remote server");
		ipField.addActionListener(this);
		bar.add(ipField);
		
		connectButton = new JButton("connect");
		connectButton.addActionListener(this);
		bar.add(connectButton);
		
		clearButton = new JButton("clear log");
		clearButton.addActionListener(this);
		bar.add(clearButton);
		
		exitButton = new JButton("Exit");
		exitButton.addActionListener(this);
		bar.add(exitButton);
		
		add(bar, BorderLayout.SOUTH);
		
		this.pack();
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				//@TODO we may need to do more cleaning up in here, like closing network connections
				System.exit(0);
			}
		});
		
		try {
			scratchMultiPlayer = new ScratchMPModel(1888);
			scratchMultiPlayer.addListener(this);
		}
		catch (IOException ioe) {
			log("couldn't start multiplayer server:");
			log(ioe.getMessage());
			ioe.printStackTrace();
		}
		
	}
	
	/**
	 * This method listens for Button clicks
	 */
	public void actionPerformed(ActionEvent event) {
		if (event.getSource() == exitButton) {
			System.exit(0);
		}
		else if (event.getSource() == clearButton) {
			log.setText(null);
		}
		else if (event.getSource() == connectButton || event.getSource() == ipField) {
			scratchMultiPlayer.connectToServer(ipField.getText());
		}
		
	}

	public void log(String line) {
		log.append("\n");
		log.append(line);
	}

}
