package com.unciv

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.GameInfo
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.BFS
import com.unciv.logic.map.MapType
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.VictoryType
import java.util.*
import kotlin.collections.ArrayList

class GameParameters{
    var difficulty="Prince"
    var mapRadius=20
    var numberOfHumanPlayers=1
    var humanNations=ArrayList<String>().apply { add("Babylon") } // Good default starting civ
    var numberOfEnemies=3
    var numberOfCityStates=0
    var mapType= MapType.Perlin
    var noBarbarians=false
    var mapFileName :String?=null
    var victoryTypes :ArrayList<VictoryType> = VictoryType.values().toCollection(ArrayList()) // By default, all victory types
}

class GameStarter{
    fun startNewGame(newGameParameters: GameParameters): GameInfo {
        val gameInfo = GameInfo()

        gameInfo.gameParameters = newGameParameters
        gameInfo.tileMap = TileMap(newGameParameters)
        gameInfo.tileMap.gameInfo = gameInfo // need to set this transient before placing units in the map

        val startingLocations = getStartingLocations(
                newGameParameters.numberOfEnemies+newGameParameters.numberOfHumanPlayers+newGameParameters.numberOfCityStates,
                gameInfo.tileMap)

        val availableCivNames = Stack<String>()
        availableCivNames.addAll(GameBasics.Nations.filter { !it.value.isCityState() }.keys.shuffled())
        availableCivNames.removeAll(newGameParameters.humanNations)
        availableCivNames.remove("Barbarians")
        val availableCityStatesNames = Stack<String>()
        availableCityStatesNames.addAll(GameBasics.Nations.filter { it.value.isCityState() }.keys.shuffled())

        for(nation in newGameParameters.humanNations) {
            val playerCiv = CivilizationInfo(nation)
            gameInfo.difficulty = newGameParameters.difficulty
            playerCiv.playerType = PlayerType.Human
            gameInfo.civilizations.add(playerCiv)
        }

        val barbarianCivilization = CivilizationInfo("Barbarians")
        gameInfo.civilizations.add(barbarianCivilization)// second is barbarian civ

        for (nationName in availableCivNames.take(newGameParameters.numberOfEnemies)) {
            val civ = CivilizationInfo(nationName)
            gameInfo.civilizations.add(civ)
        }

        for (cityStateName in availableCityStatesNames.take(newGameParameters.numberOfCityStates)) {
            val civ = CivilizationInfo(cityStateName)
            gameInfo.civilizations.add(civ)
        }

        gameInfo.setTransients() // needs to be before placeBarbarianUnit because it depends on the tilemap having its gameinfo set

        for (civInfo in gameInfo.civilizations.filter {!it.isBarbarianCivilization() && !it.isPlayerCivilization()}) {
            for (tech in gameInfo.getDifficulty().aiFreeTechs)
                civInfo.tech.addTechnology(tech)
        }

        // and only now do we add units for everyone, because otherwise both the gameInfo.setTransients() and the placeUnit will both add the unit to the civ's unit list!

        for (civ in gameInfo.civilizations.filter { !it.isBarbarianCivilization() }) {
            val startingLocation = startingLocations.pop()!!

            civ.placeUnitNearTile(startingLocation.position, Constants.settler)
            civ.placeUnitNearTile(startingLocation.position, "Warrior")
            civ.placeUnitNearTile(startingLocation.position, "Scout")
        }

        return gameInfo
    }

    fun getStartingLocations(numberOfPlayers:Int,tileMap: TileMap): Stack<TileInfo> {
        var landTiles = tileMap.values
                .filter { it.isLand && !it.getBaseTerrain().impassable }

        val landTilesInBigEnoughGroup = ArrayList<TileInfo>()
        while(landTiles.any()){
            val bfs = BFS(landTiles.random()){it.isLand && !it.getBaseTerrain().impassable}
            bfs.stepToEnd()
            val tilesInGroup = bfs.tilesReached.keys
            landTiles = landTiles.filter { it !in tilesInGroup }
            if(tilesInGroup.size > 20) // is this a good number? I dunno, but it's easy enough to change later on
                landTilesInBigEnoughGroup.addAll(tilesInGroup)
        }

        for(minimumDistanceBetweenStartingLocations in tileMap.tileMatrix.size/3 downTo 0){
            val freeTiles = landTilesInBigEnoughGroup
                    .filter {  vectorIsAtLeastNTilesAwayFromEdge(it.position,minimumDistanceBetweenStartingLocations,tileMap)}
                    .toMutableList()

            val startingLocations = ArrayList<TileInfo>()
            for(player in 0..numberOfPlayers){
                if(freeTiles.isEmpty()) break // we failed to get all the starting locations with this minimum distance
                val randomLocation = freeTiles.random()
                startingLocations.add(randomLocation)
                freeTiles.removeAll(tileMap.getTilesInDistance(randomLocation.position,minimumDistanceBetweenStartingLocations))
            }
            if(startingLocations.size < numberOfPlayers) continue // let's try again with less minimum distance!
            val stack = Stack<TileInfo>()
            stack.addAll(startingLocations)
            return stack
        }
        throw Exception("Didn't manage to get starting locations even with distance of 1?")
    }

    fun vectorIsAtLeastNTilesAwayFromEdge(vector: Vector2, n:Int, tileMap: TileMap): Boolean {
        // Since all maps are HEXAGONAL, the easiest way of checking if a tile is n steps away from the
        // edge is checking the distance to the CENTER POINT
        // Can't believe we used a dumb way of calculating this before!
        val hexagonalRadius = -tileMap.leftX
        val distanceFromCenter = HexMath().getDistance(vector, Vector2.Zero)
        return hexagonalRadius-distanceFromCenter >= n
    }

}