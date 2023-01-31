import ChessPlayerClient from '../api/chessPlayerClient';
import Header from '../components/header';
import BindingClass from '../utils/bindingClass';
import DataStore from '../utils/DataStore';

/**
 * The component that handles the logic to play chess for the website.
 */
export default class GetNextMove extends BindingClass {

    constructor() {
        super();
        this.bindClassMethods(['mount', 'setUpBoard', 'reloadMoves', 'drag', 'submitMove', 'cancel'], this);
        this.dataStore = new DataStore();
        this.header = new Header();
    }

     /**
      * Add the header to the page and load the ChessPlayerClient.
      */
    mount() {
        this.header.addHeaderToPage();
        this.client = new ChessPlayerClient();

        let url = new URL(window.location.href);
        let gameId = url.searchParams.get("gameId");
        this.dataStore.set("gameId", gameId);
        setUpBoard(game.getNotation, game.getValidMoves);

        document.getElementById('submit').addEventListener('click', this.submitMove);
        document.getElementById('submit').disabled = true;
        document.getElementById('cancel').addEventListener('click', this.cancel);
        document.getElementById('cancel').disabled = true;


        //compile all the positions
        const ranks = [1, 2, 3, 4, 5, 6, 7, 8];
        const files = ["a", "b", "c", "d", "e", "f", "g", "h"];
        var positions = [];
        for (let i = 1; i < 9; i++) {
            for (let j = 1; j < 9; j++) {
                positions.push(files[i -1] + ranks[j -1]);
            }
        }

        //add an event listener for each piece
        console.log(positions);
        positions.forEach((position) => {
            //if the position has a chess piece in it
            const child = document.getElementById(position).firstElementChild;
            if (child) {
                child.addEventListener("mousedown", this.drag);
            }
        });
    }

    setUpBoard(fen, validMoves) {

    }

    drag(event) {
        if (!document.getElementById('submit').disabled) {
            return;
        }
        console.log(event);
        const piece = event.target;
        const origParent = piece.parentElement;
        if (!piece.validMoves) {
            return;
        }
        piece.validMoves.forEach((validMove) => {
            document.getElementById(validMove).classList.add("validMove");
        });


        piece.style.position = 'absolute';
        piece.style.zIndex = 1000;

        // move it out of any current parents directly into body
        // to make it positioned relative to the body
        document.body.append(piece);

        // centers the piece at (pageX, pageY) coordinates
        function moveAt(pageX, pageY) {
            piece.style.left = pageX - piece.offsetWidth / 2 + 'px';
            piece.style.top = pageY - piece.offsetHeight / 2 + 'px';
        }

        // move our absolutely positioned piece under the pointer
        moveAt(event.pageX, event.pageY);

        // potential droppable that we're flying over right now
        let elemBelow = null;
        function onMouseMove(event) {
            moveAt(event.pageX, event.pageY);
            piece.hidden = true;
            elemBelow = document.elementFromPoint(event.clientX, event.clientY);
            piece.hidden = false;
        }

        // (2) move the piece on mousemove
        document.addEventListener('mousemove', onMouseMove);

        // (3) drop the piece, remove unneeded handlers
        piece.onmouseup = () => {
            var captured = null;
            // if you drop the piece on another piece, keep track of that piece in captured
            if (elemBelow.nodeName == "CHESS-PIECE") {
                captured = elemBelow;
                elemBelow = elemBelow.parentElement;
            }
            if (piece.validMoves.includes(elemBelow.id)) {
                if (captured) {
                    elemBelow.removeChild(captured);
                }
                elemBelow.append(piece);
                document.getElementById('submit').disabled = false;
                document.getElementById('cancel').disabled = false;
                this.dataStore.set("move", {"to": elemBelow, "from": origParent, "captured": captured});
                console.log(this.dataStore.get("move"));
            }
            else {
                origParent.append(piece);
            }
            piece.style.position = 'static';
            document.removeEventListener('mousemove', onMouseMove);
            piece.onmouseup = null;
        };
    }

    /**
     * Submit the move to the database and draw the new move.
     */
     async submitMove() {
         const errorMessageDisplay = document.getElementById('error-message');
         errorMessageDisplay.innerText = '';
         errorMessageDisplay.classList.add('hidden');

        const gameId = this.dataStore.get("gameId");
        const move = this.dataStore.get("move").from.id + this.dataStore.get("move").to.id;

        console.log(gameId);
        console.log(move);

        const submitButton = document.getElementById('submit');
        const origButtonText = submitButton.innerText;
        submitButton.innerText = 'Loading...';

        const response = await this.client.getNextMove(gameId, move, (error) => {
            submitButton.innerText = origButtonText;
            errorMessageDisplay.innerText = `Error: ${error.message}`;
            errorMessageDisplay.classList.remove('hidden');
        });

        if (response) {
            console.log(response);
            window.localStorage.setItem("notation", response.notation)
            reloadMoves(response);
        }
     }

    /**
     * Reload the board after a move
     */
     reloadMoves(response) {
        // Remove all previous valid moves
        document.getElementsByTagName("chess-piece").forEach((piece) => {
            piece.validMoves = [];
        })

        // Do the response move
        let bestMove = response.bestMove;
        let from = document.getElementById(bestMove.slice(0, 2));
        let to = document.getElementById(bestMove.slice(2, 4));
        if (to.firstElementChild) {
            to.removeChild(to.children[0]);
        }
        from.removeChild(from.children[0]);
        to.append(from.firstElementChild);

        // Make the new valid moves
        response.validMoves.forEach((move) => {
            let validFrom = move.slice(0, 2);
            let validTo = move.slice(2);
            document.getElementById(validFrom).validMoves.push(validTo);
        })
    }

     cancel() {
        const move = this.dataStore.get("move");
        const piece = move.to.removeChild(move.to.children[0]);
        if (move.captured) {
            move.to.append(move.captured);
        }
        move.from.append(piece);
        document.getElementById('submit').disabled = true;
        document.getElementById('cancel').disabled = true;
     }
}


class ChessPiece extends HTMLElement {}

/**
 * Main method to run when the page contents have loaded.
 */
const main = async () => {
    const game = new GetNextMove();
    game.mount();
};

window.addEventListener('DOMContentLoaded', main);