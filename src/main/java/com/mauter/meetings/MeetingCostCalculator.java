package com.mauter.meetings;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeetingCostCalculator extends JFrame {
    private static final long serialVersionUID = 1L;
    
    Logger log = LoggerFactory.getLogger(getClass());

    private JLabel attendeesLabel;
    private JLabel timeLabel;
    private JLabel costLabel;
    private JTextField rateField;
    private Timer timer;

    List<Event> events = new LinkedList<>();
    
    public MeetingCostCalculator() {
        setTitle("Meeting Cost Calculator");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(5, 1));

        // Timer label
        timeLabel = new JLabel("00:00:00", JLabel.CENTER);
        timeLabel.setFont(new Font("Serif", Font.BOLD, 24));
        add(timeLabel);

        // Cost label
        costLabel = new JLabel("$0.00", JLabel.CENTER);
        costLabel.setFont(new Font("Serif", Font.BOLD, 24));
        add(costLabel);

        // Attendees label
        attendeesLabel = new JLabel("Attendees: 0", JLabel.CENTER);
        add(attendeesLabel);

        // Billable rate input
        JPanel ratePanel = new JPanel();
        ratePanel.add(new JLabel("Rate ($/hr): "));
        rateField = new JTextField("0.00", 6);
        ratePanel.add(rateField);
        add(ratePanel);

        // Buttons panel
        JPanel buttonPanel = new JPanel();
        JButton startStopButton = new JButton("Start");
        JButton clearButton = new JButton("Clear");
        JButton plusButton = new JButton("+");
        JButton minusButton = new JButton("-");

        buttonPanel.add(startStopButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(plusButton);
        buttonPanel.add(minusButton);

        add(buttonPanel);

        timer = new Timer(1000, e -> { if (timer.isRunning()) updateDisplay(); });
        
        startStopButton.addActionListener(e -> {
            if (timer.isRunning()) {
                timer.stop();
                addEvent(EventType.STOP);
                startStopButton.setText("Start");
            } else {
                addEvent(EventType.START);
                timer.start();
                startStopButton.setText("Stop");
            }
        });
        
        clearButton.addActionListener(e -> {
        	timer.stop();
        	startStopButton.setText("Start");
        	
        	events.clear();
        	
            updateDisplay();
        });
        
        plusButton.addActionListener(e -> addEvent(EventType.ADD));
        minusButton.addActionListener(e -> { if (attendeeCount() > 0) addEvent(EventType.REMOVE); });
    }
    
    void addEvent(EventType type) {
    	long now = System.currentTimeMillis();
    	
    	if (!events.isEmpty()) {
    		Event last = events.getLast();
    		last.setCost(cost(now - last.when(), last.attendeeCount()));
    	}
    	
    	int attendeeCount = attendeeCount() + type.countChange;
    	
    	events.add(new Event(now, type, attendeeCount, new AtomicReference<>(BigDecimal.ZERO)));
    	
    	updateDisplay();
    }
    
    long elapsedTime() {
    	long result = 0;
    	long start = 0;
    	for (Event event : events) {
    		if (event.type() == EventType.START) start = event.when();
    		else if (event.type() == EventType.STOP) {
    			result += (event.when() - start);
    			start = 0;
    		}
    	}
    	if (start != 0) {
    		result += (System.currentTimeMillis() - start);
    	}
    	log.debug("elapsedTime={}", result);
    	return result;
    }
    
    int attendeeCount() {
    	return events.isEmpty() ? 0 : events.getLast().attendeeCount();
    }
    
    BigDecimal billableRateInMs() {
    	int msInOneHour = 1 /*hr*/ * 60 /*min*/ * 60 /*secs*/ * 1000 /*ms*/;
    	BigDecimal result;
    	try {
            result = new BigDecimal(rateField.getText())
            		.divide(new BigDecimal(msInOneHour), 10, RoundingMode.HALF_UP);
        } catch (NumberFormatException nfe) {
            result = BigDecimal.ZERO;
        }
    	log.debug("billableRateInMs={}", result);
    	return result;
    }
    
    BigDecimal cost(long elapsed, int attendees) {
    	BigDecimal result = billableRateInMs().multiply(new BigDecimal(elapsed)).multiply(new BigDecimal(attendees));
    	log.debug("cost={}", result);
    	return result;
    }
    
    BigDecimal totalCost() {
    	BigDecimal result = BigDecimal.ZERO;
    	
    	boolean foundFirstStart = false;
    	
    	for (Event event : events) {
    		if (event.type() == EventType.START) {
    			foundFirstStart = true;
    			continue;
    		}
    		if (!foundFirstStart) continue;
    		
    		result = result.add(event.cost().get());

    	}
    	
    	if (!events.isEmpty()) {
	    	Event last = events.getLast();
	    	result = result.add(cost(System.currentTimeMillis() - last.when(), last.attendeeCount()));
    	}
    	
    	log.debug("totalCost={}", result);
    	
    	return result;
    }

    private void updateDisplay() {
    	long elapsedTime = elapsedTime();
        long seconds = (elapsedTime / 1000) % 60;
        long minutes = (elapsedTime / 60000) % 60;
        long hours = (elapsedTime / 3600000) % 24;
        
        timeLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
        costLabel.setText(NumberFormat.getCurrencyInstance().format(totalCost()));
        attendeesLabel.setText(String.format("Attendees: %d", attendeeCount()));
    }
    
    public static void main(String[] args) {
    	System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
    	System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS");
    	System.setProperty("org.slf4j.simpleLogger.showShortLogName", "true");
    	System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");
    	System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
    	
        SwingUtilities.invokeLater(() -> new MeetingCostCalculator().setVisible(true));
    }
}

enum EventType {
	START(0),
	ADD(1),
	REMOVE(-1),
	STOP(0);
	
	int countChange;
	
	private EventType(int countChange) { this.countChange = countChange; }
}


record Event(long when, EventType type, int attendeeCount, AtomicReference<BigDecimal> cost) {
	public void setCost(BigDecimal cost) {
		this.cost.set(cost);
	}
}
