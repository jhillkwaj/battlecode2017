package mainplayer;
import java.util.Random;

import battlecode.common.*;




public strictfp class RobotPlayer {

	////////////////////////////////////////
	// SIGNAL ARRAY (add new signals here)
	///////////////////////////////////////
	// 0 - rush
	// 1 - guess loc
	// 2 - Enemy Sighting x
	// 3 - Enemy Sighting y
	// 4 - Archon count
	// 5 - Gardener count
	// 6 - Soldier count
	// 7 - Lumberjack count
	// 8 - Tank Count
	// 9 - Scout Count
	// 10 - unused
	// 11+ unused
	////////////////////////////////////////


	static float[] buildOrder = {2, 1, 0, 0, 8};
	static RobotController rc;
	static RobotType type; // robot's type
	static Direction targetDirection = null; // Direction to move in
	static int combat = -100; // set to 1 to go into combat, < 0 to avoid at that range, 0 to scout

	static int[] signalLoc = new int[2];
	static boolean broadcastDeath = false; // broadcasted death
	static BulletInfo[] nearbyBullets; // all incoming bullets
	static boolean charge;

	static Random random;
	
	static boolean rush = true;

	/**
	 * run() is the method that is called when a robot is instantiated in the Battlecode world.
	 * If this method returns, the robot dies!
	 **/
	@SuppressWarnings("unused")
	public static void run(RobotController rc) throws GameActionException {
		// This is the RobotController object. You use it to perform actions from this robot,
		// and to get information on its current status.
		RobotPlayer.rc = rc;
		type = rc.getType();
		random = new Random(rc.getID());
		
		if(rc.readBroadcast(0) == 0)
			rush = false;
		
		if(!rush) {
			buildOrder[0] = 6;
			buildOrder[1] = 3;
			buildOrder[2] = 0;
			buildOrder[3] = 0;
			buildOrder[4] = 2;
		}

		// Here, we've separated the controls into a different method for each RobotType.
		// You can add the missing ones or rewrite this into your own control structure.
		switch (type) {
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
				runScout();
				break;
		}
	}

	//Archon code. Has the highest bytecode so it is best to do command type stuff here
	static void runArchon() throws GameActionException {
		// Get enemy team
		Team enemy = rc.getTeam().opponent();

		//flee to range of 10
		combat = -1000;

		//broadcast unit creation
		rc.broadcast(4,rc.readBroadcast(4) + 1);

		// find enemy archon starting locations
		MapLocation[] oppArchons = rc.getInitialArchonLocations(enemy);


		// The code you want your robot to perform every round should be in this loop
		while (true) {
			// Try/catch blocks stop unhandled exceptions, which cause your robot to explode
			try {
				// Check for incoming bullets
				nearbyBullets = rc.senseNearbyBullets();
				// TODO: FIND A WAY TO AVOID INCOMING BULLETS

				// Check for death
				if(!broadcastDeath && rc.getHealth() < 20) {
					rc.broadcast(4,rc.readBroadcast(4) - 1);
					broadcastDeath = true;
				}

				// Get my location
				MapLocation myLoc = rc.getLocation();

				// Find the enemy on the map
				if(rc.readBroadcast(1) == 0) {
					MapLocation startLoc = rc.getInitialArchonLocations(enemy)[0];
					rc.broadcast(2, (int)startLoc.x);
					rc.broadcast(3, (int)startLoc.y);
					
					rc.broadcast(1, 1);
					
					loadData();
				}


				// Produce gardeners
				if(chooseProduction(true) == 0) { // check if I should
					Direction dir = new Direction(myLoc, oppArchons[0]); // direction roughly facing opponent
					int num = 0;
					while (!rc.canHireGardener(dir) && num < 8) { // rotate until creation is clear
						num++;
						dir.rotateLeftDegrees(num * ((float) Math.PI / 4));
					}
					if (rc.canHireGardener(dir)) { // if possible, create
						rc.hireGardener(dir);
						rc.broadcast(5,rc.readBroadcast(5) + 1);
					}
				}


				// Deal with nearby enemies
				RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy); // find nearby enemies
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
				wander(10);

				// Tries to shake a tree
				canPickTrees();
				
				saveData();

				// Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
				Clock.yield();

			} catch (Exception e) {
				System.out.println("Archon Exception");
				e.printStackTrace();
			}
		}
	}

	static void runGardener() throws GameActionException {
		// Get teams
		Team enemy = rc.getTeam().opponent(); // enemy
		Team myTeam = rc.getTeam(); // mine

		// combat
		combat = -60;

		Direction buildAxis = null;

		boolean water = false;

		// The code you want your robot to perform every round should be in this loop
		while (true) {
			// Try/catch blocks stop unhandled exceptions, which cause your robot to explode
			try {
				// Check for incoming bullets
				nearbyBullets = rc.senseNearbyBullets();

				// broadcast death
				if(!broadcastDeath && rc.getHealth() < 20) {
					rc.broadcast(5,rc.readBroadcast(5) - 1);
					broadcastDeath = true;
				}

				//call for help'
				// TODO: FIX
				RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);
				if (robots.length > 0) {
					rc.broadcast(2,(int)robots[0].location.x);
					rc.broadcast(3,(int)robots[0].location.y);
				}

				// Generate a random direction
				Direction dir;
				if(buildAxis == null)
					dir = randomDirection();
				else
					dir = buildAxis;

				// Attempt to build a robot
				int buildType = chooseProduction(false);
				if (buildAxis != null && buildType == 1 && rc.canBuildRobot(RobotType.SOLDIER, dir)) {
					rc.buildRobot(RobotType.SOLDIER, dir);
				} else if (buildAxis != null && buildType == 2 && rc.canBuildRobot(RobotType.LUMBERJACK, dir)) {
					rc.buildRobot(RobotType.LUMBERJACK, dir);
				}
				else if (buildAxis != null && buildType == 4 && rc.canBuildRobot(RobotType.SCOUT, dir)) {
					rc.buildRobot(RobotType.SCOUT, dir);
				}

				// Plant trees
				 if((buildAxis != null || ( (!rc.isCircleOccupied(rc.getLocation().add(dir, 2.01f), 1) 
						 && rc.senseNearbyTrees(5, rc.getTeam()).length == 0 ) && rc.onTheMap(rc.getLocation().add(dir, 2.01f), 1) ))) {

					for(int addDir = 60; addDir < 360; addDir += 60) {
						if(rc.canPlantTree(dir.rotateRightDegrees(addDir))) {
							rc.plantTree(dir.rotateRightDegrees(addDir));
							buildAxis = dir;
							water = true;
						}
					}
				}

				//Get the info on the targeted tree
				TreeInfo treeInfo = null;
				boolean canWaterTreeInfo = false;
				//if it can't be sensed then pick a new tree
				TreeInfo[] trees = rc.senseNearbyTrees();
				for(TreeInfo tree : trees) {
					//Pick a new tree to target
					//TODO use a better algorithm for doing this. Maybe check for bullets or robots or pick closest one
					if(tree.getTeam() == myTeam) {
						boolean canWaterTree = rc.canWater(tree.ID);
						if(water && (treeInfo == null || (tree.health < treeInfo.health && (canWaterTree || !canWaterTreeInfo))
								|| (canWaterTree && !canWaterTreeInfo))) {
							treeInfo = tree;
							canWaterTreeInfo = canWaterTree;
						}
					}
				}

				// if a tree is targeted move closer and water it if close enough
				if(treeInfo != null) {
					if(canWaterTreeInfo) {
						rc.water(treeInfo.ID);
					}
					else { // *** when will this be necessary
						tryMove(directionTwords(rc.getLocation(), treeInfo.location));
						if(!rc.hasMoved()) {
							wander(10);
						}
					}
				}
				else {
					// Move randomly
					//TODO improve on this
					if(!rc.hasMoved())
						wander(10);
				}

				canPickTrees();

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


		RobotInfo myGardener = null;
		RobotInfo[] temp = rc.senseNearbyRobots(-1, rc.getTeam());
		for(int i=0; i<temp.length; i++) {
			if(temp[i].type == RobotType.GARDENER && rush) myGardener = temp[i];
		}
		rc.broadcast(6,rc.readBroadcast(6) + 1);
		//if(myGardener == null)

		// The code you want your robot to perform every round should be in this loop
		while (true) {
			combat = 1;
			// Try/catch blocks stop unhandled exceptions, which cause your robot to explode
			try {
				if(!broadcastDeath && rc.getHealth() < 20) {
					rc.broadcast(6,rc.readBroadcast(6) - 1);
					broadcastDeath = true;
				}

				MapLocation myLocation = rc.getLocation();

				// See if there are any nearby enemy robots
				RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

				// If there are some...
				if (robots.length > 0) {

					int closest = 0;
					float smallestDistance = Float.MAX_VALUE;

					for(int i = 0; i < robots.length; i++) {
						float distanceTo = robots[i].getLocation().distanceTo(rc.getLocation());
						if(distanceTo < smallestDistance && (rc.getRoundNum() > 300 || robots[i].type != RobotType.ARCHON)) {
							closest = i;
							smallestDistance = distanceTo;
						}
					}

					if(robots[closest].type != RobotType.ARCHON) {
						rc.broadcast(2,(int)robots[closest].location.x);
						rc.broadcast(3,(int)robots[closest].location.y);

						//fire three shots against multiple enemies, gardeners, or archons
						if ( rc.canFireTriadShot() && ( robots[closest].type == RobotType.GARDENER
								|| robots[closest].type == RobotType.ARCHON || robots.length > 1 ) ) {

							rc.fireTriadShot(myLocation.directionTo(robots[closest].location));
						}

						// And we have enough bullets, and haven't attacked yet this turn...
						if (rc.canFireSingleShot()) {
							// ...Then fire a bullet in the direction of the enemy.
							rc.fireSingleShot(myLocation.directionTo(robots[closest].location));

						}

						nearbyBullets = rc.senseNearbyBullets();

						//move closer to the enemy
						if(robots[closest].type == RobotType.ARCHON || robots[closest].type == RobotType.GARDENER
								|| robots[closest].type == RobotType.SCOUT || smallestDistance > rc.getType().sensorRadius  * .7f) {
							MapLocation myLoc = rc.getLocation();

							//calculate the radius of the enemy
							int enemyRad = 1;
							if(robots[closest].type == RobotType.ARCHON )
								enemyRad = 2;

							//if you can move right next to the enemy
							if(smallestDistance - 1 - enemyRad <= rc.getType().strideRadius &&
									rc.canMove(directionTwords( myLoc, robots[closest].location), smallestDistance - 1 - enemyRad) ) {

								if(safeToMove(rc.getLocation().add(directionTwords( myLoc, robots[closest].location),
										smallestDistance - 1 - enemyRad))) {

									rc.move(directionTwords( myLoc, robots[closest].location), smallestDistance - 1 - enemyRad);


								}


							}
							else { //just move closer
								tryMove(directionTwords( myLoc, robots[closest].location));

							}
						}
						else //move further away from the enemy
							tryMove(directionTwords( robots[closest].location, rc.getLocation()));
					}
					else {
						nearbyBullets = rc.senseNearbyBullets();
					}
				}
				else {
					nearbyBullets = rc.senseNearbyBullets();
				}
				
				if(myGardener != null) {
					robots = rc.senseNearbyRobots(-1, rc.getTeam());
					boolean closeToGardener = false;
					boolean tooCloseToGardener = false;
					myLocation = rc.getLocation();
					for(int i=0; !closeToGardener && i<robots.length; i++) {
						if(robots[i].type == RobotType.GARDENER) {
							if(robots[i].ID == myGardener.ID) closeToGardener = true;
							if(rc.getLocation().distanceTo(robots[i].location)<myGardener.getRadius()+4) tooCloseToGardener = true;
						}
					}
					if(!closeToGardener) { // too far
						tryMove(myLocation.directionTo(myGardener.location));
					}
					if(tooCloseToGardener) { // too close
						tryMove(randomDirection());
					}
					if(!rc.hasMoved()) { // otherwise
						Direction dir = myGardener.location.directionTo(myLocation);
						dir.rotateLeftDegrees(3);
						tryMove(myLocation.directionTo(myGardener.location.add(dir, myGardener.location.distanceTo(myLocation))));
					}
				}
				else {
					wander(10);
				}
				canPickTrees();

				// Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
				Clock.yield();

			} catch (Exception e) {
				System.out.println("Soldier Exception");
				e.printStackTrace();
			}
		}
	}


	static void runScout() throws GameActionException {
		// get enemy team
		Team enemy = rc.getTeam().opponent();

		//broadcast unit creation
		rc.broadcast(9,rc.readBroadcast(9) + 1);

		// target only gardeners if 1
		float p = rc.readBroadcast(10);

		// The code you want your robot to perform every round should be in this loop
		while (true) {
			// Try/catch blocks stop unhandled exceptions, which cause your robot to explode
			try {
				System.out.println(" ***** Scout " + rc.getID() + ": " + p + " *****");

				// engage enemy in combat
				combat = 1;

				// broadcast death
				if(!broadcastDeath && rc.getHealth() < 15) {
					rc.broadcast(9,rc.readBroadcast(9) - 1);
					broadcastDeath = true;
				}

				// get my location
				MapLocation myLocation = rc.getLocation();

				// Deal with nearby robots
				RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy); // See if there are any nearby enemy robots
				if (robots.length > 0) { // If there are some...
					// find the closest robot
					int closest = 0;
					int closestGardener = -1;
					float smallestDistance = Float.MAX_VALUE;
					float smallestGardenerDist = Float.MAX_VALUE;
					for(int i = 0; i < robots.length; i++) {
						float distanceTo = robots[i].getLocation().distanceTo(rc.getLocation());
						if(distanceTo < smallestDistance && (rc.getRoundNum() > 300 || robots[i].type != RobotType.ARCHON) ) {
							closest = i;
							smallestDistance = distanceTo;
						}
						if(robots[i].type == RobotType.GARDENER && (closestGardener==-1 || distanceTo<smallestGardenerDist)) {
							closestGardener = i;
							smallestGardenerDist = distanceTo;
						}
					}

					if(rc.getRoundNum() > 300 || robots[closest].type != RobotType.ARCHON) { // If past round 300, or the closest isn't an Archon

						rc.broadcast(2,(int)robots[closest].location.x);
						rc.broadcast(3,(int)robots[closest].location.y);

						// And we have enough bullets, and haven't attacked yet this turn...
						if (rc.canFireSingleShot()) {
							// ...Then fire a bullet in the direction of the enemy.
							rc.fireSingleShot(myLocation.directionTo(robots[closest].location));
						}

						nearbyBullets = rc.senseNearbyBullets();

						// Move based on type of sensed robot
						if(closestGardener!=-1) { // if there is a gardener nearby
							System.out.println(" ***** MOVING TO NEAREST GARDENER!! *****");
							MapLocation myLoc = rc.getLocation();

							//calculate the radius of the enemy
							float enemyRad = robots[closestGardener].type.bodyRadius;

							//if you can move right next to the enemy
							if(smallestDistance - 1 - enemyRad <= rc.getType().strideRadius &&
									rc.canMove(directionTwords( myLoc, robots[closestGardener].location), smallestDistance - 1 - enemyRad) ) {
								if(safeToMove(rc.getLocation().add(directionTwords( myLoc, robots[closestGardener].location),
										smallestDistance - 1 - enemyRad))) {
									rc.move(directionTwords( myLoc, robots[closestGardener].location), smallestDistance - 1 - enemyRad);
								}
							}
							else { //just move closer
								tryMove(directionTwords( myLoc, robots[closestGardener].location));
							}
						}
						else if((p==1) && (robots[closest].type == RobotType.ARCHON || robots[closest].type == RobotType.GARDENER ||
								robots[closest].type == RobotType.SCOUT || smallestDistance > rc.getType().sensorRadius * .7)) {  // no gardener nearby
							System.out.println(" ***** THERE IS NO GARDENER AND THIS IS AN ERROR ***** ");
							MapLocation myLoc = rc.getLocation();

							//calculate the radius of the enemy
							float enemyRad = robots[closest].type.bodyRadius;

							//if you can move right next to the enemy
							if(smallestDistance - 1 - enemyRad <= rc.getType().strideRadius &&
									rc.canMove(directionTwords( myLoc, robots[closest].location), smallestDistance - 1 - enemyRad) ) {
								if(safeToMove(rc.getLocation().add(directionTwords( myLoc, robots[closest].location),
										smallestDistance - 1 - enemyRad))) {
									rc.move(directionTwords( myLoc, robots[closest].location), smallestDistance - 1 - enemyRad);
								}
							}
							else { //just move closer
								tryMove(directionTwords( myLoc, robots[closest].location));
							}
						}
						else {//move further away from the enemy
							if (p == 1) {
								System.out.println(" ***** ERROR #2 ****");
								tryMove(directionTwords(robots[closest].location, rc.getLocation()));
							}
						}
					}
					else { // within round 300 and closest is archon
						nearbyBullets = rc.senseNearbyBullets();
					}
				}
				else { // no robots nearby
					nearbyBullets = rc.senseNearbyBullets();
				}


				// Move randomly
				if(!rc.hasMoved())
					wander(10);

				canPickTrees();

				// Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
				Clock.yield();

			} catch (Exception e) {
				System.out.println("Scout Exception");
				e.printStackTrace();
			}
		}
	}

	static void runLumberjack() throws GameActionException {
		// get the enemy team
		Team enemy = rc.getTeam().opponent();

		// scout for trees
		combat = 0;

		// trees to chop down
		int targetTree = -1;
		boolean standStill = false;

		//broadcast unit creation
		rc.broadcast(7,rc.readBroadcast(7) + 1);

		// The code you want your robot to perform every round should be in this loop
		while (true) {
			// Try/catch blocks stop unhandled exceptions, which cause your robot to explode
			try {
				// sense nearby bullets
				charge = false;
				nearbyBullets = rc.senseNearbyBullets();

				// broadcast death
				if(!broadcastDeath && rc.getHealth() < 20) {
					rc.broadcast(7,rc.readBroadcast(7) - 1);
					broadcastDeath = true;
				}

				// Attack any nearby robots
				RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy); // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
				if(robots.length > 0 && !rc.hasAttacked()) { // if so, attack
					rc.strike();
				}

				// CHOP NEUTRAL/ENEMY TREES
				// Get the info on the targeted tree
				TreeInfo treeInfo = null;
				standStill = false;
				if(targetTree != -1) { // we have a target tree
					if(rc.canSenseTree(targetTree)) // it is within sensor radius
						treeInfo = rc.senseTree(targetTree); // sense the tree
					else
						targetTree = -1; // unset the target
				}
				if(targetTree == -1){ // no target tree
					TreeInfo[] trees = rc.senseNearbyTrees(); // get nearby trees
					if(trees.length > 0) { // if there is a tree nearby
						// choose closest tree not on our team
						float smallestDistanceToTree = Float.MAX_VALUE;
						for(int i = 0; i < trees.length; i++) {
							float distanceTo = trees[i].getLocation().distanceTo(rc.getLocation());
							if(trees[i].team != rc.getTeam() && distanceTo < smallestDistanceToTree) {
								treeInfo = trees[i];
								targetTree = treeInfo.ID;
								smallestDistanceToTree = distanceTo;
							}
						}
					}
				}

				// if a tree is targeted move closer to it or chop it
				if(treeInfo != null) {
					//System.out.println("try chop " + treeInfo.ID);
					if(rc.canChop(treeInfo.ID)) { // chop the tree
						rc.chop(treeInfo.ID);
						standStill = true;
					}
					else if (!rc.hasMoved()) { // move towards it
						tryMove(directionTwords(rc.getLocation(), treeInfo.location));
					}
				}

				if(!standStill && robots.length == 0) { // no close trees or robots
					robots = rc.senseNearbyRobots(-1, enemy); // find all nearby robots

					// Move towards the closest enemy robot
					if (robots.length > 0) {
						// find closest
						int closest = 0;
						float smallestDistance = Float.MAX_VALUE;
						for (int i = 0; i < robots.length; i++) {
							float distanceTo = robots[i].getLocation().distanceTo(rc.getLocation());
							if (distanceTo < smallestDistance) {
								closest = i;
								smallestDistance = distanceTo;
							}
						}

						//broadcast its location
						rc.broadcast(2, (int) robots[closest].location.x);
						rc.broadcast(3, (int) robots[closest].location.y);

						//and move closer to it
						charge = true;
						tryMove(directionTwords(rc.getLocation(), robots[closest].location));
					}
				}

				// Move randomly
				// TODO improve on this
				if(!standStill && !rc.hasMoved()) {
					wander(10);
				}

				canPickTrees();

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
		return new Direction(random.nextFloat() * 2 * (float)Math.PI);
	}

	static Direction directionTwords(MapLocation objLoc, MapLocation towardsLoc) {
		return new Direction(objLoc, towardsLoc);
	}


	static void wander(int tries) throws GameActionException {
		if(tries <= 0)
			return;

		if(combat != 0 && targetDirection != null) {
			//run to
			int broadcastOne = rc.readBroadcast(2);
			int broadcastTwo = rc.readBroadcast(3);
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
					if(myLoc.distanceSquaredTo(newLoc) < -combat) {
						targetDirection = new Direction(newLoc, myLoc);
						signalLoc[0] = broadcastOne;
						signalLoc[1] = broadcastTwo;
					}
				}
			}
		}


		if(targetDirection == null)
			targetDirection = randomDirection();

		if(!tryMove(targetDirection)) {
			targetDirection = null;
			wander(tries - 1);
		}


	}

	static int chooseProduction(boolean archon) throws GameActionException {

		// Trade in for VP at the last round
		float bullets = rc.getTeamBullets();
		if(bullets >= 10000 || rc.getRoundNum() >= rc.getRoundLimit() - 1) {
			rc.donate(bullets);
		}

		//Quick fix TODO clean this up
		float[] armyRatios;
		
		if(!rush) {
		//Calculate the number of each unit compared to the desired number
			float[] aRatios = {(float)rc.readBroadcast(5) / buildOrder[0],
					((float)rc.readBroadcast(6) ) / buildOrder[1], (float)rc.readBroadcast(7) / buildOrder[2],
					(float)rc.readBroadcast(8) / buildOrder[3], (float)rc.readBroadcast(9) / buildOrder[4]}; // {gardener, soldier, lumberjack, tank, scout
			armyRatios = aRatios;
		}
		else {
			float[] aRatios = {(float)rc.readBroadcast(5) / buildOrder[0],
					((float)rc.readBroadcast(6) + 1) / buildOrder[1], (float)rc.readBroadcast(7) / buildOrder[2],
					(float)rc.readBroadcast(8) / buildOrder[3], (float)rc.readBroadcast(9) / buildOrder[4]};
			armyRatios = aRatios;
		}
		
		
		//store the best one to create
		int bestRatio = -1;

		//iterate through each unit type and select the best one to produce next
		for(int i = ((archon||rc.readBroadcast(4)>0)?0:1); i < armyRatios.length; i++) {
			//System.out.println(" " + armyRatios[i] + " " + armyRatios[bestRatio]);
			if(bestRatio<0 || (armyRatios[i] < armyRatios[bestRatio] && buildOrder[i]!=0)) {
				bestRatio = i;
			}
		}

		// TODO: Determine whether there are trees on the map
		if(archon && rc.readBroadcast(5)==0) return 0;
		if(rush && rc.readBroadcast(9)==0) return 4;
		
		if(bestRatio != 0 && rc.senseNearbyTrees(-1, Team.NEUTRAL).length > 0 && rc.readBroadcast(5) >= 2)
    		return 2;

		System.out.println("** bestRatio : " + bestRatio);
		System.out.println("** Gardener  : #=" + rc.readBroadcast(5) + "; /=" + rc.readBroadcast(5)/buildOrder[0]);
		System.out.println("** Soldier   : #=" + rc.readBroadcast(6) + "; /=" + rc.readBroadcast(6)/buildOrder[1]);
		System.out.println("** Lumberjack: #=" + rc.readBroadcast(7) + "; /=" + rc.readBroadcast(7)/buildOrder[2]);
		System.out.println("** Tank      : #=" + rc.readBroadcast(8) + "; /=" + rc.readBroadcast(8)/buildOrder[3]);
		System.out.println("** Scout     : #=" + rc.readBroadcast(9) + "; /=" + rc.readBroadcast(9)/buildOrder[4]);

		//System.out.println("Build " + bestRatio);
		return bestRatio;
	}

	public static void canPickTrees() throws GameActionException {
		//You can't do it the way it was done it Rahul player because it generates exceptions in some 
		//cases and breaks gardeners when modified to not generate exceptions for some reason
    	TreeInfo[] nearbyTrees = rc.senseNearbyTrees();
    	for(TreeInfo tree : nearbyTrees) {
    		if(tree.containedBullets > 0 && !rc.hasAttacked() && rc.canShake(tree.ID)) {
    			rc.shake(tree.ID);
    		}
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
		if (rc.canMove(dir) && safeToMove(rc.getLocation().add(dir, type.strideRadius))) {
			rc.move(dir);
			return true;
		}

		// Now try a bunch of similar angles
		boolean moved = false;
		int currentCheck = 1;

		while(currentCheck<=checksPerSide) {
			// Try the offset of the left side
			if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))
					&& safeToMove(rc.getLocation().add(dir.rotateLeftDegrees(degreeOffset*currentCheck), type.strideRadius))) {
				rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
				return true;
			}
			// Try the offset on the right side
			if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))
					&& safeToMove(rc.getLocation().add(dir.rotateRightDegrees(degreeOffset*currentCheck), type.strideRadius))) {
				rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
				return true;
			}
			// No move performed, try slightly further
			currentCheck++;
		}

		// A move never happened, so return false.
		return false;
	}

	static boolean safeToMove(MapLocation moveLoc) {
		if(charge || !willBulletHitMe(moveLoc))
			return true;
		return false;
	}

	static boolean willBulletHitMe(MapLocation mapLocation){
		for(int i = 0; i < nearbyBullets.length && i < 6; i++) {
			if(willCollideWithMe(nearbyBullets[i],mapLocation)) {
				return true;
			}
		}
		return false;
	}
	
	static void saveData() throws GameActionException {
		int unitCount = rc.readBroadcast(5) + rc.readBroadcast(6) + rc.readBroadcast(7) + rc.readBroadcast(8) + rc.readBroadcast(9);
		
		rc.setTeamMemory(0, rush ? 2 : 1);
		rc.setTeamMemory(1, unitCount);
	}
	
	
	static void loadData() throws GameActionException {
		int lastRush = (int)rc.getTeamMemory()[0];
		int lastUnitCount = (int)rc.getTeamMemory()[1];
		
		if(lastUnitCount < 6) {
			rush = lastRush == 2 ? false : true;
		}
		else {
			rush = lastRush == 2 ? true : false;
		}
		
		if(!rush) {
			rc.setIndicatorDot(rc.getLocation(), 0, 0, 200);
			rc.broadcast(0, 0);
			buildOrder[0] = 6;
			buildOrder[1] = 3;
			buildOrder[2] = 0;
			buildOrder[3] = 0;
			buildOrder[4] = 2;
		}
		else {
			rc.setIndicatorDot(rc.getLocation(), 200, 0, 0);
			rc.broadcast(0, 1);
			buildOrder[0] = 2;
			buildOrder[1] = 1;
			buildOrder[2] = 0;
			buildOrder[3] = 0;
			buildOrder[4] = 8;
		}
		
	}

	/**
	 * A slightly more complicated example function, this returns true if the given bullet is on a collision
	 * course with the current robot. Doesn't take into account objects between the bullet and this robot.
	 *
	 * @param bullet The bullet in question
	 * @return True if the line of the bullet's path intersects with this robot's current position.
	 */
	static boolean willCollideWithMe(BulletInfo bullet, MapLocation myLocation) {



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