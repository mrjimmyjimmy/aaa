package automail;

import exceptions.ExcessiveDeliveryException;
import exceptions.ItemTooHeavyException;
import exceptions.MailAlreadyDeliveredException;
import mailItems.MailItem;
import mailItems.PriorityMailItem;
import robots.RobotTypesRegister;
import exceptions.FragileItemBrokenException;
import exceptions.FragileItemCannotDeliverException;
import exceptions.HeavyItemCannotDeliverException;
import strategies.Automail;
import strategies.IMailPool;
import util.Building;
import util.Clock;
import util.RobotSetting.RobotType;
import java.util.stream.Stream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class simulates the behaviour of AutoMail
 */
public class Simulation {
	
    /** Constant for the mail generator */
    private static int MAIL_TO_CREATE;
    

    private static ArrayList<MailItem> MAIL_DELIVERED;
    private static double total_score = 0;

    public static void main(String[] args) throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, HeavyItemCannotDeliverException, FragileItemCannotDeliverException {
    	PropertyReader propertyReader = new PropertyReader("automail.properties");
    	Properties automailProperties = propertyReader.loadInputProperty();
    	
		// MailPool
		String mailPoolName = automailProperties.getProperty("MailPool");
		IMailPool mailPool = (IMailPool) Class.forName(mailPoolName).newInstance();
		
		//Seed
		String seedProp = automailProperties.getProperty("Seed");
		// Floors
		Building.FLOORS = Integer.parseInt(automailProperties.getProperty("Floors"));
        System.out.printf("Floors: %5d%n", Building.FLOORS);
        // Fragile
        boolean fragile = Boolean.parseBoolean(automailProperties.getProperty("Fragile"));
        System.out.printf("Fragile: %5b%n", fragile);
		// Mail_to_Create
		MAIL_TO_CREATE = Integer.parseInt(automailProperties.getProperty("Mail_to_Create"));
        System.out.printf("Mail_to_Create: %5d%n", MAIL_TO_CREATE);
		// Last_Delivery_Time
		Clock.LAST_DELIVERY_TIME = Integer.parseInt(automailProperties.getProperty("Last_Delivery_Time"));
        System.out.printf("Last_Delivery_Time: %5d%n", Clock.LAST_DELIVERY_TIME);
		// Robots
		String robotsProp = automailProperties.getProperty("Robots");
		List<RobotType> robotTypes = Stream.of(robotsProp.split(",")).map(RobotType::valueOf).collect(Collectors.toList());
		System.out.print("Robots: "); System.out.println(robotTypes);

		// End properties
        MAIL_DELIVERED = new ArrayList<MailItem>();
                
        /** Used to see whether a seed is initialized or not */
        HashMap<Boolean, Integer> seedMap = new HashMap<>();
        
        /** Read the first argument and save it as a seed if it exists */
        if (args.length == 0 ) { // No arg
        	if (seedProp == null) { // and no property
        		seedMap.put(false, 0); // so randomise
        	} else { // Use property seed
        		seedMap.put(true, Integer.parseInt(seedProp));
        	}
        } else { // Use arg seed - overrides property
        	seedMap.put(true, Integer.parseInt(args[0]));
        }
        Integer seed = seedMap.get(true);
        System.out.printf("Seed: %s%n", seed == null ? "null" : seed.toString());
        RobotTypesRegister typeResgister = new RobotTypesRegister(robotTypes);
        Automail automail = new Automail(mailPool, new ReportDelivery(), robotTypes);
        MailGenerator mailGenerator = new MailGenerator(MAIL_TO_CREATE, automail.mailPool, seedMap, fragile);
        mailPool.connectRegister(typeResgister);
        /** Initiate all the mail */
        mailGenerator.generateAllMail();
        // PriorityMailItem priority;  // Not used in this version
        while(MAIL_DELIVERED.size() != mailGenerator.MAIL_TO_CREATE) {
        	//System.out.println("-- Step: "+Clock.Time());
            /* priority = */ 
            mailGenerator.step();
            try {
                automail.mailPool.step();
				for (int i=0; i<robotTypes.size(); i++) automail.robot[i].step();
			} catch (ExcessiveDeliveryException|ItemTooHeavyException|FragileItemBrokenException e) {
				e.printStackTrace();
				System.out.println("Simulation unable to complete.");
				System.exit(0);
			} catch (HeavyItemCannotDeliverException|FragileItemCannotDeliverException e) {
				e.printStackTrace();
				System.out.println("Unable to deliever all mails because need a particular robot type to delivery the item, please check automail.properties.");
				System.exit(0);
			}
            Clock.Tick();
        }
        printResults();
    }
    
    static class ReportDelivery implements IMailDelivery {
    	
    	/** Confirm the delivery and calculate the total score */
    	public void deliver(MailItem deliveryItem){
    		if(!MAIL_DELIVERED.contains(deliveryItem)){
                System.out.printf("T: %3d > Delivered [%s]%n", Clock.Time(), deliveryItem.toString());
    			MAIL_DELIVERED.add(deliveryItem);
    			// Calculate delivery score
    			total_score += calculateDeliveryScore(deliveryItem);
    		}
    		else{
    			try {
    				throw new MailAlreadyDeliveredException();
    			} catch (MailAlreadyDeliveredException e) {
    				e.printStackTrace();
    			}
    		}
    	}

    }
    
    private static double calculateDeliveryScore(MailItem deliveryItem) {
    	// Penalty for longer delivery times
    	final double penalty = 1.2;
    	double priority_weight = 0;
        // Take (delivery time - arrivalTime)**penalty * (1+sqrt(priority_weight))
    	if(deliveryItem instanceof PriorityMailItem){
    		priority_weight = ((PriorityMailItem) deliveryItem).getPriorityLevel();
    	}
        return Math.pow(Clock.Time() - deliveryItem.getArrivalTime(),penalty)*(1+Math.sqrt(priority_weight));
    }

    public static void printResults(){
        System.out.println("T: "+Clock.Time()+" | Simulation complete!");
        System.out.println("Final Delivery time: "+Clock.Time());
        System.out.printf("Final Score: %.2f%n", total_score);
    }
}
