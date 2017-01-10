package testplayer;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    
    //direction to move in
    static Direction targetDirection = null;
    //set to 1 to go into combat, -1 to avoid, 0 to scout
    static int combat = -1;
    //has this unity seen the enemy yet
    static boolean seenEnemy = false;
    //
    static int[] signalLoc = new int[2];

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
    **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
            case TANK:
                runSoldier();
                break;
            case SCOUT:
            	runSoldier();
                break;
        }
	}

    //Archon code. Has the highest bytecode so it is best to do command type stuff here
    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");
        Team enemy = rc.getTeam().opponent();
        combat = -1;
        

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Generate a random direction
                Direction dir = randomDirection();

                // Randomly attempt to build a gardener in this direction
                // TODO come up with a better way to decide when to do this
                if ( rc.canHireGardener(dir) && (Math.random() < .01 || rc.getTeamBullets() > 150)) {
                    rc.hireGardener(dir);
                }
                
                
                //spot enemies and call for help
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);
                if (robots.length > 0) {
                	rc.broadcast(2,(int)robots[0].location.x);
                    rc.broadcast(3,(int)robots[0].location.y);
                }
                else {
                	//TODO clear the broadcasts if old
                	
                	//TODO send units to an enemy location (spy on enemy brodcasts)
                }

                // Move randomly
                //TODO improve on this
                wander();

//                // Broadcast archon's location for other robots on the team to know
//                //TODO use this in some way or get rid of it
//                MapLocation myLocation = rc.getLocation();
//                rc.broadcast(0,(int)myLocation.x);
//                rc.broadcast(1,(int)myLocation.y);
                
                //TODO buy victory points

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

	static void runGardener() throws GameActionException {
        System.out.println("I'm a gardener!");
        Team enemy = rc.getTeam().opponent();
        combat = -1;

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Listen for home archon's location
            	//TODO why do we need this? we don't use it
                int xPos = rc.readBroadcast(0);
                int yPos = rc.readBroadcast(1);
                MapLocation archonLoc = new MapLocation(xPos,yPos);

                // Generate a random direction
                Direction dir = randomDirection();
                
                //call for help
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);
                if (robots.length > 0) {
                	rc.broadcast(2,(int)robots[0].location.x);
                    rc.broadcast(3,(int)robots[0].location.y);
                }

                // Randomly attempt to build a soldier or lumberjack in this direction
                //TODO come up with a better way for doing this
                if (rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < .1) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                } else if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && Math.random() < .1 && rc.isBuildReady()) {
                    rc.buildRobot(RobotType.LUMBERJACK, dir);
                }
                
               
            	//TODO plant and water trees

                // Move randomly
                //TODO improve on this
                wander();

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }
	

    static void runSoldier() throws GameActionException {
        System.out.println("I'm an soldier!");
        Team enemy = rc.getTeam().opponent();
        

        // The code you want your robot to perform every round should be in this loop
        while (true) {
        	combat = 1;
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

	                
                // If there are some...
                if (robots.length > 0) {
                	rc.broadcast(2,(int)robots[0].location.x);
                    rc.broadcast(3,(int)robots[0].location.y);
                    // And we have enough bullets, and haven't attacked yet this turn...
                    if (rc.canFireSingleShot()) {
                        // ...Then fire a bullet in the direction of the enemy.
                        rc.fireSingleShot(rc.getLocation().directionTo(robots[0].location));
                        
                    }
                    combat = -1;
                }
              

                	// Move randomly
                	wander();
                

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack!");
        Team enemy = rc.getTeam().opponent();
        combat = 0;
        int targetTree = 1;
        boolean shakeTree = false;

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

               
                if(robots.length > 0) {
                	rc.broadcast(2,(int)robots[0].location.x);
                    rc.broadcast(3,(int)robots[0].location.y);
                }
                
                if(robots.length > 0 && !rc.hasAttacked()) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
                } else {
                    // No close robots, so search for robots within sight radius
                    robots = rc.senseNearbyRobots(-1,enemy);

                    // If there is a robot, move towards it
                    if(robots.length > 0) {
                    	rc.broadcast(2,(int)robots[0].location.x);
                        rc.broadcast(3,(int)robots[0].location.y);
                    	
                        MapLocation myLocation = rc.getLocation();
                        MapLocation enemyLocation = robots[0].getLocation();
                        Direction toEnemy = myLocation.directionTo(enemyLocation);

                        tryMove(toEnemy);
                    } 
                }
                
               //chop down neutral or enemy trees tree
                
               //Get the info on the targeted tree
               TreeInfo treeInfo = null;
               if(targetTree != -1) {
            	   if(rc.canSenseTree(targetTree))
            		   treeInfo = rc.senseTree(targetTree);
            	   else
            		   targetTree = -1;
               }
               if(targetTree == -1){
            	   //if it can't be sensed then pick a new tree
            	   TreeInfo[] trees = rc.senseNearbyTrees();
            	   if(trees.length > 0) {
            		   //Pick a new tree to target
            		   //TODO use a better algorithm for doing this. Maybe check for bullets or robots or pick closest one
            		   if((trees[0].getTeam() == Team.NEUTRAL && trees[0].containedRobot != null) || trees[0].getTeam() == enemy) {
            			   treeInfo = trees[0];
            			   targetTree = treeInfo.ID;
            			   shakeTree = false;
            			   combat = 0;
            		   }
            		   else if(trees[0].containedBullets > 5) {
            			   treeInfo = trees[0];
            			   targetTree = treeInfo.ID;
            			   shakeTree = true;
            			   combat = 0;
            		   }
            		   else {
            			   //if no trees around use in combat
            			   combat = 1;
            		   }
            	   }
               }
               // if a tree is targeted move closer to it or chop it
	       		if(treeInfo != null) {
	       			if(treeInfo.getTeam() == Team.NEUTRAL || treeInfo.getTeam() == enemy ) {
	       				
	       				if(!rc.hasMoved() && !rc.canChop(treeInfo.ID)) {
		        			System.out.println("Tree!");
		        			tryMove(directionTwords( rc.getLocation(), treeInfo.location));
		        		}
	       				
	       				if(!shakeTree && rc.canChop(treeInfo.ID) && !rc.hasAttacked()) {
		        			System.out.println("Chop");
		        			rc.chop(treeInfo.ID);
		        		}
	       				
	       				if(shakeTree && rc.canShake(treeInfo.ID) && !rc.hasAttacked()) {
		        			System.out.println("Shake");
		        			rc.shake(treeInfo.ID);
		        			targetTree = -1;
		        		}
		        		
	       			}
                }

            	
            	// Move randomly
	       		// TODO improve on this
            	if(!rc.hasMoved()) {
            		System.out.println("Wander");
            		wander();
            	}

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
    
    static Direction directionTwords(MapLocation objLoc, MapLocation towardsLoc) {
		return new Direction(objLoc, towardsLoc);
	}
    
    
    static void wander() throws GameActionException {
    	
    	if(combat != 0 && targetDirection != null) {
    		//run to
    		int broadcastOne = rc.readBroadcast(2);
    		int broadcastTwo = rc.readBroadcast(3);
    		System.out.println(" " + broadcastOne + " " + broadcastTwo);
    		if( (broadcastOne != 0 || broadcastTwo != 0) && (signalLoc[0] != broadcastOne || signalLoc[1] != broadcastTwo)) {
    			MapLocation newLoc = new MapLocation(broadcastOne, broadcastTwo);
    			if(combat > 0) {
    				
    				targetDirection = new Direction(rc.getLocation(), newLoc);
    				signalLoc[0] = broadcastOne;
    				signalLoc[1] = broadcastTwo;
    			}
    			else if(combat < 0) { 
    				//run away if close
    				MapLocation myLoc = rc.getLocation();
    				if(myLoc.distanceSquaredTo(newLoc) < 100) {
	    				targetDirection = new Direction(newLoc, myLoc);
	    				signalLoc[0] = broadcastOne;
	    				signalLoc[1] = broadcastTwo;
    				}
    			}
    		}
    	}
    	
    	
    	if(targetDirection == null || Math.random() < .1f)
    		targetDirection = randomDirection();
    	
    	if(!tryMove(targetDirection)) {
    		targetDirection = null;
    		wander();
    	}
	
    	
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir,20,3);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir The intended direction of movement
     * @param degreeOffset Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            // Try the offset on the right side
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    /**
     * A slightly more complicated example function, this returns true if the given bullet is on a collision
     * course with the current robot. Doesn't take into account objects between the bullet and this robot.
     *
     * @param bullet The bullet in question
     * @return True if the line of the bullet's path intersects with this robot's current position.
     */
    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }
}
