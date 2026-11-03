const elements = {
    setupPanel: document.querySelector("#setup-panel"),
    gameLayout: document.querySelector("#game-layout"),
    handoffPanel: document.querySelector("#handoff-panel"),
    resultsPanel: document.querySelector("#results-panel"),
    newGameForm: document.querySelector("#new-game-form"),
    difficulty: document.querySelector("#difficulty"),
    soloMode: document.querySelector("#solo-mode"),
    playerFields: document.querySelector("#player-fields"),
    addPlayerButton: document.querySelector("#add-player-button"),
    guessForm: document.querySelector("#guess-form"),
    guessInput: document.querySelector("#guess-input"),
    nextClueButton: document.querySelector("#next-clue-button"),
    giveUpButton: document.querySelector("#give-up-button"),
    restartButton: document.querySelector("#restart-button"),
    resultsRestartButton: document.querySelector("#results-restart-button"),
    readyButton: document.querySelector("#ready-button"),
    boardPanel: document.querySelector("#board-panel"),
    boardTitle: document.querySelector("#board-title"),
    boardSubtitle: document.querySelector("#board-subtitle"),
    boardScoreList: document.querySelector("#board-score-list"),
    boardTrack: document.querySelector("#board-track"),
    boardContinueButton: document.querySelector("#board-continue-button"),
    boardContinueLabel: document.querySelector("#board-continue-label"),
    soundToggle: document.querySelector("#sound-toggle"),
    themeToggle: document.querySelector("#theme-toggle"),
    score: document.querySelector("#score"),
    round: document.querySelector("#round"),
    roundLimit: document.querySelector("#round-limit"),
    journeyLabel: document.querySelector("#journey-label"),
    roundTrack: document.querySelector("#round-track"),
    currentDifficulty: document.querySelector("#current-difficulty"),
    scoreboardKicker: document.querySelector("#scoreboard-kicker"),
    scoreboardTitle: document.querySelector("#scoreboard-title"),
    scoreboardDescription: document.querySelector("#scoreboard-description"),
    category: document.querySelector("#category"),
    cardRound: document.querySelector("#card-round"),
    clueNumber: document.querySelector("#clue-number"),
    clueText: document.querySelector("#clue-text"),
    clueProgress: document.querySelector("#clue-progress"),
    presenterMessage: document.querySelector("#presenter-message"),
    availablePoints: document.querySelector("#available-points"),
    toast: document.querySelector("#toast"),
    clueCard: document.querySelector("#clue-card"),
    currentPlayer: document.querySelector("#current-player"),
    turnAvatar: document.querySelector("#turn-avatar"),
    turnProgress: document.querySelector("#turn-progress"),
    playerScoreList: document.querySelector("#player-score-list"),
    handoffPlayer: document.querySelector("#handoff-player"),
    winnerTitle: document.querySelector("#winner-title"),
    resultsSubtitle: document.querySelector("#results-subtitle"),
    resultsList: document.querySelector("#results-list"),
    celebration: document.querySelector("#celebration")
};

const difficultyLabels = {
    FACIL: "FÁCIL",
    MEDIO: "MÉDIO",
    DIFICIL: "DIFÍCIL",
    EXTREMO: "EXTREMO"
};

const categoryLabels = {
    PERSONALIDADES_HISTORICAS: "PERSONALIDADES HISTÓRICAS",
    CELEBRIDADES: "CELEBRIDADES",
    FILMES_E_SERIES: "FILMES E SÉRIES",
    DESENHOS_E_ANIMES: "DESENHOS E ANIMES",
    GEOGRAFIA: "GEOGRAFIA",
    HISTORIA: "HISTÓRIA",
    CIENCIA: "CIÊNCIA",
    TECNOLOGIA: "TECNOLOGIA",
    ANIMAIS: "ANIMAIS",
    ESPORTES: "ESPORTES",
    CULTURA_GERAL: "CULTURA GERAL",
    CURIOSIDADES: "CURIOSIDADES"
};

const pointsByClue = [20, 18, 16, 14, 12, 10, 8, 6, 4, 2];
const playerAvatars = [
    { id: "fox", name: "Raposa" },
    { id: "owl", name: "Coruja" },
    { id: "tiger", name: "Tigre" },
    { id: "dragon", name: "Dragão" },
    { id: "axolotl", name: "Axolote" },
    { id: "cat", name: "Gato" },
    { id: "rabbit", name: "Coelho" },
    { id: "octopus", name: "Polvo" }
];
let currentGame = null;
let requestInProgress = false;
let toastTimeout;
let soundsEnabled = localStorage.getItem("perfil8-sounds") !== "off";
let audioContext;
let lastTypingSound = 0;
const initialTheme = localStorage.getItem("perfil8-theme") === "dark" ? "dark" : "light";
const mobileBoardLayout = window.matchMedia("(max-width: 520px)");
document.documentElement.dataset.theme = initialTheme;
updateThemeToggle(initialTheme);
updateSoundToggle();
initializePlayerAvatarPickers();
updatePlayerMode();

document.addEventListener("click", (event) => {
    const control = event.target instanceof Element ? event.target.closest("button, input, select, a") : null;
    if (control && !control.disabled) playSound("click");
}, true);

elements.difficulty.addEventListener("change", () => playSound("select"));
elements.soloMode.addEventListener("change", updatePlayerMode);
elements.addPlayerButton.addEventListener("click", () => {
    const playerCount = elements.playerFields.querySelectorAll(".player-input-row").length;
    if (playerCount >= 8) return;
    const nextNumber = Math.max(...[...elements.playerFields.querySelectorAll("input")].map((input) =>
        Number(input.id.replace("player-", "")))) + 1;
    const row = document.createElement("div");
    row.className = "player-input-row";
    const label = document.createElement("label");
    label.htmlFor = `player-${nextNumber}`;
    label.textContent = `Jogador ${nextNumber}`;
    const input = document.createElement("input");
    input.id = `player-${nextNumber}`;
    input.name = "players";
    input.type = "text";
    input.maxLength = 30;
    input.autocomplete = "off";
    input.required = true;
    input.value = `Jogador ${nextNumber}`;
    const removeButton = document.createElement("button");
    removeButton.className = "remove-player-button";
    removeButton.type = "button";
    removeButton.setAttribute("aria-label", `Remover ${label.textContent}`);
    removeButton.textContent = "×";
    row.append(label, input, removeButton);
    row.append(createAvatarPicker("fox", label.textContent));
    elements.playerFields.append(row);
    updatePlayerMode();
    input.focus();
});
elements.playerFields.addEventListener("click", (event) => {
    if (!(event.target instanceof Element)) return;
    const avatarChoice = event.target.closest(".avatar-choice");
    if (avatarChoice) {
        const picker = avatarChoice.closest(".avatar-picker");
        picker.querySelectorAll(".avatar-choice").forEach((choice) => {
            const selected = choice === avatarChoice;
            choice.classList.toggle("is-selected", selected);
            choice.setAttribute("aria-pressed", String(selected));
        });
        return;
    }
    if (!event.target.matches(".remove-player-button")
        || elements.playerFields.querySelectorAll(".player-input-row").length <= 1) return;
    event.target.closest(".player-input-row").remove();
    updatePlayerMode();
});
elements.guessInput.addEventListener("input", () => {
    const now = performance.now();
    if (now - lastTypingSound < 75) return;
    lastTypingSound = now;
    playSound("type");
});
elements.guessInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && elements.guessInput.value.trim()) playSound("submit");
});

elements.soundToggle.addEventListener("click", () => {
    soundsEnabled = !soundsEnabled;
    localStorage.setItem("perfil8-sounds", soundsEnabled ? "on" : "off");
    updateSoundToggle();
});

elements.themeToggle.addEventListener("click", () => {
    const theme = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    document.documentElement.dataset.theme = theme;
    localStorage.setItem("perfil8-theme", theme);
    document.querySelector('meta[name="theme-color"]').content = theme === "dark" ? "#17231e" : "#f6f0df";
    updateThemeToggle(theme);
    playSound("theme");
});

elements.newGameForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    await runRequest(async () => {
        const game = await api("/api/games", {
            method: "POST",
            body: JSON.stringify({
                difficulty: elements.difficulty.value,
                players: [...elements.playerFields.querySelectorAll(".player-input-row:not([hidden]) input")]
                    .map((input) => input.value.trim()),
                playerAvatars: [...elements.playerFields.querySelectorAll(".player-input-row:not([hidden]) .avatar-choice.is-selected")]
                    .map((choice) => choice.dataset.avatar)
            })
        });
        currentGame = game;
        elements.setupPanel.hidden = true;
        elements.handoffPanel.hidden = true;
        elements.boardPanel.hidden = true;
        elements.resultsPanel.hidden = true;
        elements.gameLayout.hidden = false;
        renderGame(game);
        animateClueReveal();
        elements.guessInput.focus();
    });
});

elements.guessForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!currentGame || !elements.guessInput.value.trim()) return;

    await runRequest(async () => {
        const result = await api(`/api/games/${currentGame.gameId}/guess`, {
            method: "POST",
            body: JSON.stringify({ guess: elements.guessInput.value.trim() })
        });
        currentGame = result.game;
        renderGame(result.game);
        elements.guessInput.value = "";

        if (result.correct) {
            document.body.classList.add("is-win");
            playSound("win");
            showToast(result.message, "success");
            window.setTimeout(() => document.body.classList.remove("is-win"), 700);
            finishTurn(result.game);
        } else if (result.revealedAnswer) {
            playSound("wrong");
            showToast(result.message, "error");
            finishTurn(result.game);
        } else {
            document.body.classList.remove("is-wrong");
            void elements.guessInput.offsetWidth;
            document.body.classList.add("is-wrong");
            playSound("wrong");
            showToast(result.message, "error");
            window.setTimeout(() => document.body.classList.remove("is-wrong"), 350);
        }
    });
});

elements.nextClueButton.addEventListener("click", async () => {
    if (!currentGame) return;
    await runRequest(async () => {
        if (currentGame.players.length > 1) {
            currentGame = await api(`/api/games/${currentGame.gameId}/pass-turn`, { method: "POST" });
            renderGame(currentGame);
            finishTurn(currentGame);
            return;
        }
        currentGame = await api(`/api/games/${currentGame.gameId}/clue`, { method: "POST" });
        renderGame(currentGame);
        animateClueReveal();
    });
});

elements.boardContinueButton.addEventListener("click", async () => {
    if (!currentGame) return;
    if (currentGame.competitionFinished) {
        showCompetitionResults(currentGame);
        return;
    }
    if (currentGame.players.length === 1 && currentGame.roundNumber >= currentGame.maxRounds) {
        showSoloResults(currentGame);
        return;
    }
    await runRequest(async () => {
        currentGame = await api(`/api/games/${currentGame.gameId}/next-round`, { method: "POST" });
        elements.boardPanel.hidden = true;
        if (currentGame.players.length > 1) {
            showHandoff(currentGame);
            return;
        }
        renderGame(currentGame);
        animateClueReveal();
        elements.guessInput.focus();
    });
});

mobileBoardLayout.addEventListener("change", () => {
    if (currentGame && !elements.boardPanel.hidden) {
        renderBoard(currentGame, currentGame.players.length === 1 ? 200 : 20);
    }
});

elements.readyButton.addEventListener("click", () => {
    if (!currentGame) return;
    elements.handoffPanel.hidden = true;
    elements.boardPanel.hidden = true;
    elements.gameLayout.hidden = false;
    renderGame(currentGame);
    animateClueReveal();
    elements.guessInput.focus();
});

elements.giveUpButton.addEventListener("click", async () => {
    if (!currentGame || !window.confirm("Quer desistir desta rodada e revelar a resposta?")) return;
    await runRequest(async () => {
        const result = await api(`/api/games/${currentGame.gameId}/give-up`, { method: "POST" });
        currentGame = result.game;
        renderGame(result.game);
        finishTurn(result.game);
        showToast(result.message, "error");
    });
});

elements.restartButton.addEventListener("click", () => {
    returnToSetup();
});
elements.resultsRestartButton.addEventListener("click", returnToSetup);

function animateClueReveal() {
    elements.clueCard.classList.remove("is-revealing");
    void elements.clueCard.offsetWidth;
    elements.clueCard.classList.add("is-revealing");
    elements.clueCard.addEventListener("animationend", () => {
        elements.clueCard.classList.remove("is-revealing");
    }, { once: true });
    playSound("reveal");
}

function updateSoundToggle() {
    elements.soundToggle.textContent = `SOM: ${soundsEnabled ? "LIGADO" : "DESLIGADO"}`;
    elements.soundToggle.setAttribute("aria-pressed", String(soundsEnabled));
    const label = soundsEnabled ? "Desativar sons" : "Ativar sons";
    elements.soundToggle.setAttribute("aria-label", label);
    elements.soundToggle.title = label;
}

function updateThemeToggle(theme) {
    const isDark = theme === "dark";
    elements.themeToggle.textContent = `TEMA: ${isDark ? "ESCURO" : "CLARO"}`;
    elements.themeToggle.setAttribute("aria-pressed", String(isDark));
    const label = isDark ? "Ativar tema claro" : "Ativar tema escuro";
    elements.themeToggle.setAttribute("aria-label", label);
    elements.themeToggle.title = label;
}

function playSound(kind) {
    if (!soundsEnabled || !window.AudioContext) return;
    audioContext ??= new AudioContext();
    if (audioContext.state === "suspended") void audioContext.resume();

    const patterns = {
        click: [[740, 0.055], [980, 0.045]],
        type: [[540, 0.035]],
        select: [[620, 0.06], [820, 0.07]],
        submit: [[660, 0.07], [880, 0.09]],
        theme: [[440, 0.08], [660, 0.08], [880, 0.1]],
        reveal: [[440, 0.09], [554, 0.09], [660, 0.12]],
        win: [[523, 0.11], [659, 0.11], [784, 0.11], [1047, 0.2]],
        wrong: [[240, 0.12], [180, 0.16]]
    };
    let offset = 0;
    for (const [frequency, duration] of patterns[kind]) {
        const oscillator = audioContext.createOscillator();
        const gain = audioContext.createGain();
        const startAt = audioContext.currentTime + offset;
        oscillator.type = "square";
        oscillator.frequency.setValueAtTime(frequency, startAt);
        gain.gain.setValueAtTime(0.0001, startAt);
        gain.gain.exponentialRampToValueAtTime(0.1, startAt + 0.008);
        gain.gain.exponentialRampToValueAtTime(0.0001, startAt + duration);
        oscillator.connect(gain);
        gain.connect(audioContext.destination);
        oscillator.start(startAt);
        oscillator.stop(startAt + duration);
        offset += duration + 0.025;
    }
}

async function api(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...options.headers
        }
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw new Error(result.message || result.detail || "Não foi possível concluir a solicitação.");
    }
    return result;
}

async function runRequest(action) {
    if (requestInProgress) return;
    requestInProgress = true;
    setControlsDisabled(true);
    try {
        await action();
    } catch (error) {
        showToast(error.message || "Ocorreu um erro inesperado.", "error");
    } finally {
        requestInProgress = false;
        setControlsDisabled(false);
    }
}

function renderGame(game) {
    currentGame = game;
    elements.score.textContent = game.score;
    const soloMode = game.players.length === 1;
    const playerRound = game.playerRoundNumber;
    elements.round.textContent = `${String(playerRound).padStart(2, "0")} / ${soloMode ? game.maxRounds : 1}`;
    elements.roundLimit.textContent = soloMode ? `de ${game.maxRounds} cartas` : "carta do jogador";
    elements.cardRound.textContent = `${String(playerRound).padStart(2, "0")} / ${soloMode ? game.maxRounds : 1}`;
    elements.journeyLabel.textContent = soloMode
        ? `CARTA ${String(playerRound).padStart(2, "0")} DE ${game.maxRounds}`
        : `JOGADOR ${game.roundNumber} DE ${game.maxRounds}`;
    elements.roundTrack.setAttribute("aria-valuenow", String(game.roundNumber));
    elements.roundTrack.setAttribute("aria-valuemax", String(game.maxRounds));
    elements.roundTrack.replaceChildren();
    for (let round = 1; round <= game.maxRounds; round++) {
        const step = document.createElement("span");
        step.className = "round-step";
        step.classList.toggle("is-complete", round < game.roundNumber);
        step.classList.toggle("is-current", round === game.roundNumber);
        step.setAttribute("aria-hidden", "true");
        elements.roundTrack.append(step);
    }
    elements.currentDifficulty.textContent = difficultyLabels[game.difficulty] || game.difficulty;
    elements.category.textContent = categoryLabels[game.category] || game.category;
    elements.clueNumber.textContent = String(game.clueNumber).padStart(2, "0");
    elements.clueText.textContent = game.clue;
    elements.presenterMessage.textContent = game.presenterMessage;
    elements.currentPlayer.textContent = game.currentPlayer;
    elements.turnProgress.textContent = soloMode
        ? `CARTA ${playerRound} DE ${game.maxRounds}`
        : `JOGADOR ${game.roundNumber} DE ${game.maxRounds}`;
    const currentPlayer = game.players[soloMode ? 0 : game.roundNumber - 1];
    elements.turnAvatar.querySelector("use").setAttribute("href", `/avatars.svg#${currentPlayer.avatarId}`);
    elements.scoreboardKicker.textContent = soloMode ? "MODO SOLO" : "COMPETIÇÃO";
    elements.scoreboardTitle.textContent = soloMode ? "Sua pontuação" : "Corrida ao vivo";
    elements.scoreboardDescription.textContent = soloMode
        ? "Até 20 pontos por carta: cada ponto avança uma casa."
        : "Cada ponto vale uma casa. Quem lidera a corrida?";
    elements.availablePoints.textContent = pointsByClue[game.clueNumber - 1] || 0;
    renderPlayerScores(game);
    elements.clueProgress.replaceChildren();

    for (let clue = 1; clue <= game.totalClues; clue++) {
        const segment = document.createElement("span");
        segment.classList.toggle("active", clue <= game.clueNumber);
        elements.clueProgress.append(segment);
    }

    const isActive = game.roundStatus === "ACTIVE";
    elements.guessInput.disabled = !isActive;
    elements.guessForm.querySelector("button").disabled = !isActive;
    elements.nextClueButton.disabled = !isActive || (soloMode && !game.hasNextClue);
    elements.nextClueButton.textContent = soloMode ? "Revelar próxima pista" : "Passe a vez";
    elements.giveUpButton.disabled = !isActive;
    elements.guessInput.placeholder = isActive ? "Digite seu palpite..." : "Rodada encerrada";
}

function showHandoff(game) {
    elements.gameLayout.hidden = true;
    elements.boardPanel.hidden = true;
    elements.handoffPanel.hidden = false;
    elements.handoffPlayer.textContent = game.currentPlayer;
    elements.handoffPanel.scrollIntoView({ behavior: "smooth", block: "center" });
}

function renderPlayerScores(game) {
    elements.playerScoreList.replaceChildren();
    const highestScore = Math.max(...game.players.map((player) => player.score), 0);
    const rankedPlayers = game.players
        .map((player, index) => ({ player, index }))
        .sort((first, second) => second.player.score - first.player.score || first.index - second.index);
    for (const [rank, { player, index }] of rankedPlayers.entries()) {
        const row = document.createElement("li");
        row.className = "player-score";
        row.classList.toggle("is-current", index === (game.players.length === 1 ? 0 : game.roundNumber - 1));
        row.classList.toggle("is-leader", rank === 0 && highestScore > 0);
        const position = document.createElement("span");
        position.className = "player-score-rank";
        position.setAttribute("role", "img");
        position.setAttribute("aria-label", `${rank + 1}º lugar`);
        position.textContent = rank === 0 ? "♛" : String(rank + 1).padStart(2, "0");
        const details = document.createElement("span");
        details.className = "player-score-details";
        const avatar = createAvatarImage(player.avatarId, "player-avatar");
        const name = document.createElement("span");
        name.className = "player-score-name";
        name.textContent = player.name;
        const meter = document.createElement("span");
        meter.className = "player-score-meter";
        meter.setAttribute("aria-hidden", "true");
        const fill = document.createElement("span");
        fill.className = "player-score-fill";
        fill.style.width = `${highestScore > 0 ? Math.max(0, player.score / highestScore * 100) : 0}%`;
        meter.append(fill);
        details.append(avatar, name, meter);
        const score = document.createElement("strong");
        score.textContent = `${player.score} pts`;
        row.append(position, details, score);
        elements.playerScoreList.append(row);
    }
}

function updatePlayerMode() {
    const soloMode = elements.soloMode.checked;
    const rows = [...elements.playerFields.querySelectorAll(".player-input-row")];
    rows.forEach((row, index) => {
        const hidden = soloMode && index > 0;
        row.hidden = hidden;
        row.querySelector("input").disabled = hidden || requestInProgress;
        const removeButton = row.querySelector(".remove-player-button");
        if (removeButton) removeButton.disabled = requestInProgress || rows.length <= 1;
        row.querySelectorAll(".avatar-choice").forEach((choice) => {
            choice.disabled = hidden || requestInProgress;
        });
    });
    elements.soloMode.disabled = requestInProgress;
    elements.addPlayerButton.hidden = soloMode;
    elements.addPlayerButton.disabled = requestInProgress || soloMode || rows.length >= 8;
}

function initializePlayerAvatarPickers() {
    [...elements.playerFields.querySelectorAll(".player-input-row")].forEach((row, index) => {
        row.append(createAvatarPicker(playerAvatars[index % playerAvatars.length].id, `Jogador ${index + 1}`));
    });
}

function createAvatarPicker(selectedAvatar, playerName) {
    const picker = document.createElement("div");
    picker.className = "avatar-picker";
    picker.setAttribute("role", "group");
    picker.setAttribute("aria-label", `Avatar de ${playerName}`);
    for (const avatar of playerAvatars) {
        const choice = document.createElement("button");
        const selected = avatar.id === selectedAvatar;
        choice.type = "button";
        choice.className = "avatar-choice";
        choice.classList.toggle("is-selected", selected);
        choice.dataset.avatar = avatar.id;
        choice.title = avatar.name;
        choice.setAttribute("aria-label", avatar.name);
        choice.setAttribute("aria-pressed", String(selected));
        const image = createAvatarImage(avatar.id);
        choice.append(image);
        picker.append(choice);
    }
    return picker;
}

function createAvatarImage(avatarId, className = "", accessibleName = "") {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", "0 0 80 80");
    if (className) svg.classList.add(...className.split(" "));
    if (accessibleName) {
        svg.setAttribute("role", "img");
        svg.setAttribute("aria-label", accessibleName);
    } else {
        svg.setAttribute("aria-hidden", "true");
    }
    const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
    use.setAttribute("href", `/avatars.svg#${avatarId}`);
    svg.append(use);
    return svg;
}

function finishTurn(game) {
    showBoard(game);
}

function showBoard(game) {
    elements.gameLayout.hidden = true;
    elements.handoffPanel.hidden = true;
    elements.resultsPanel.hidden = true;
    elements.boardPanel.hidden = false;
    const currentPlayerIndex = game.players.length === 1 ? 0 : game.roundNumber - 1;
    const currentPlayer = game.players[currentPlayerIndex];
    const earned = currentPlayer.pointsAwarded;
    const boardLength = game.players.length === 1 ? 200 : 20;
    elements.boardTitle.textContent = `Fim da rodada de ${currentPlayer.name}`;
    elements.boardSubtitle.textContent = earned > 0
        ? `${currentPlayer.name} ganhou ${earned} pontos e avançou ${earned} casas.`
        : `${currentPlayer.name} não marcou pontos nesta rodada.`;
    elements.boardContinueLabel.textContent = game.competitionFinished
        || game.players.length === 1 && game.roundNumber >= game.maxRounds
        ? "Ver resultado final"
        : game.hasNextTurn ? "Tabuleiro e próximo jogador" : "Próxima carta";
    document.querySelector(".board-finish-badge").lastChild.textContent = ` CHEGADA · ${boardLength}`;
    renderBoard(game, boardLength);
    elements.boardPanel.scrollIntoView({ behavior: "smooth", block: "start" });
}

function renderBoard(game, boardLength) {
    elements.boardTrack.replaceChildren();
    const boardColumns = game.players.length === 1 && !mobileBoardLayout.matches ? 10 : 5;
    const boardRows = Math.ceil(boardLength / boardColumns);
    elements.boardTrack.style.gridTemplateColumns = `repeat(${boardColumns + 1}, minmax(36px, 1fr))`;
    elements.boardTrack.style.gridTemplateRows = `repeat(${boardRows}, 58px)`;
    elements.boardTrack.style.minWidth = boardColumns === 10 ? "650px" : "300px";
    elements.boardTrack.setAttribute("aria-label", `Tabuleiro com ${boardLength} casas`);
    elements.boardTrack.parentElement.setAttribute(
        "aria-label",
        `Tabuleiro com ${boardLength} casas; role para percorrer`
    );
    const cells = new Map();
    for (let position = 0; position <= boardLength; position++) {
        const cell = document.createElement("div");
        cell.className = `board-cell${position === 0 ? " is-start" : ""}${position === boardLength ? " is-finish" : ""}`;
        const row = Math.floor(Math.max(0, position - 1) / boardColumns);
        const columnOffset = (position - 1 + boardColumns) % boardColumns;
        cell.style.gridRow = String(row + 1);
        cell.style.gridColumn = position === 0
            ? "1"
            : String(row % 2 === 0 ? columnOffset + 2 : boardColumns + 1 - columnOffset);
        if (position < boardLength) {
            const nextRow = Math.floor(position / boardColumns);
            const nextColumnOffset = (position + boardColumns) % boardColumns;
            const nextColumn = position === 0
                ? 2
                : nextRow % 2 === 0 ? nextColumnOffset + 2 : boardColumns + 1 - nextColumnOffset;
            cell.classList.add(nextRow === row
                ? nextColumn > Number(cell.style.gridColumn) ? "has-link-right" : "has-link-left"
                : "has-link-down");
        }
        if (position > 0 && position < boardLength && position % 10 === 0) {
            cell.classList.add("is-checkpoint");
        }
        const link = document.createElement("span");
        link.className = "board-cell-link";
        link.setAttribute("aria-hidden", "true");
        cell.append(link);
        const number = document.createElement("span");
        number.className = "board-cell-number";
        number.textContent = position === 0 ? "INÍCIO" : String(position);
        cell.append(number);
        const markers = document.createElement("span");
        markers.className = "board-cell-markers";
        cell.append(markers);
        cells.set(position, markers);
        elements.boardTrack.append(cell);
    }

    elements.boardScoreList.replaceChildren();
    const rankedPlayers = game.players
        .map((player, index) => ({ player, index }))
        .sort((first, second) => second.player.boardPosition - first.player.boardPosition || first.index - second.index);
    for (const { player, index } of rankedPlayers) {
        const position = Math.min(player.boardPosition, boardLength);
        const marker = createAvatarImage(player.avatarId, "board-token", `${player.name}, casa ${player.boardPosition}`);
        marker.title = `${player.name} · ${player.score} pontos · casa ${player.boardPosition}`;
        const playerMarkers = cells.get(position);
        playerMarkers.closest(".board-cell").classList.add("has-player");
        playerMarkers.append(marker);

        const entry = document.createElement("div");
        entry.className = "board-player";
        entry.classList.toggle("is-current", index === (game.players.length === 1 ? 0 : game.roundNumber - 1));
        const avatar = createAvatarImage(player.avatarId, "player-avatar");
        const details = document.createElement("span");
        details.className = "board-player-details";
        const name = document.createElement("strong");
        name.textContent = player.name;
        const progress = document.createElement("small");
        progress.textContent = `Casa ${player.boardPosition} · ${player.score} pts`;
        details.append(name, progress);
        entry.append(avatar, details);
        elements.boardScoreList.append(entry);
    }
}

function showSoloResults(game) {
    const player = game.players[0];
    elements.boardPanel.hidden = true;
    elements.gameLayout.hidden = true;
    elements.handoffPanel.hidden = true;
    elements.resultsPanel.hidden = false;
    elements.winnerTitle.textContent = "Partida concluída!";
    elements.resultsSubtitle.textContent =
        `${player.name}, você terminou ${game.roundNumber} rodadas com ${player.score} pontos.`;
    elements.resultsList.replaceChildren();
    const row = document.createElement("li");
    row.className = "result-row is-winner";
    const rank = document.createElement("span");
    rank.className = "result-rank";
    rank.textContent = "01";
    const details = document.createElement("span");
    details.className = "result-details";
    const avatar = createAvatarImage(player.avatarId, "player-avatar");
    const name = document.createElement("strong");
    name.textContent = player.name;
    const outcome = document.createElement("small");
    outcome.textContent = "Pontuação final";
    details.append(avatar, name, outcome);
    const score = document.createElement("strong");
    score.className = "result-score";
    score.textContent = `${player.score} pts`;
    row.append(rank, details, score);
    elements.resultsList.append(row);
    showResultsCelebration();
}

function showCompetitionResults(game) {
    elements.boardPanel.hidden = true;
    elements.gameLayout.hidden = true;
    elements.handoffPanel.hidden = true;
    elements.resultsPanel.hidden = false;
    const highestScore = Math.max(...game.players.map((player) => player.score));
    const winners = game.players.filter((player) => player.score === highestScore);
    elements.winnerTitle.textContent = winners.length === 1
        ? `${winners[0].name} venceu!`
        : "A competição terminou empatada!";
    elements.resultsSubtitle.textContent = winners.length === 1
        ? `${highestScore} pontos — que jogada!`
        : `Empate com ${highestScore} pontos para cada vencedor.`;
    elements.resultsList.replaceChildren();
    [...game.players]
        .sort((first, second) => second.score - first.score)
        .forEach((player, index) => {
            const row = document.createElement("li");
            row.className = "result-row";
            if (player.score === highestScore) row.classList.add("is-winner");
            const rank = document.createElement("span");
            rank.className = "result-rank";
            rank.textContent = String(index + 1).padStart(2, "0");
            const details = document.createElement("span");
            details.className = "result-details";
            const avatar = createAvatarImage(player.avatarId, "player-avatar");
            const name = document.createElement("strong");
            name.textContent = player.name;
            const outcome = document.createElement("small");
            outcome.textContent = player.correct ? "Acertou sua carta" : "Não acertou desta vez";
            details.append(avatar, name, outcome);
            const score = document.createElement("strong");
            score.className = "result-score";
            score.textContent = `${player.score} pts`;
            row.append(rank, details, score);
            elements.resultsList.append(row);
        });
    showResultsCelebration();
}

function showResultsCelebration() {
    elements.celebration.replaceChildren();
    for (let piece = 0; piece < 18; piece++) {
        const confetti = document.createElement("span");
        confetti.className = "confetti-piece";
        confetti.style.left = `${(piece * 47) % 100}%`;
        confetti.style.animationDelay = `${(piece % 7) * -0.18}s`;
        elements.celebration.append(confetti);
    }
    playSound("win");
    elements.resultsPanel.scrollIntoView({ behavior: "smooth", block: "center" });
}

function returnToSetup() {
    elements.gameLayout.hidden = true;
    elements.handoffPanel.hidden = true;
    elements.boardPanel.hidden = true;
    elements.resultsPanel.hidden = true;
    elements.setupPanel.hidden = false;
    elements.setupPanel.scrollIntoView({ behavior: "smooth", block: "center" });
    elements.difficulty.focus();
}

function setControlsDisabled(disabled) {
    elements.newGameForm.querySelector("button").disabled = disabled;
    updatePlayerMode();
    elements.guessForm.querySelector("button").disabled = disabled || !currentGame || currentGame.roundStatus !== "ACTIVE";
    elements.nextClueButton.disabled = disabled || !currentGame || currentGame.roundStatus !== "ACTIVE"
        || (currentGame.players.length === 1 && !currentGame.hasNextClue);
    elements.giveUpButton.disabled = disabled || !currentGame || currentGame.roundStatus !== "ACTIVE";
    elements.restartButton.disabled = disabled;
    elements.boardContinueButton.disabled = disabled;
}

function showToast(message, kind = "") {
    window.clearTimeout(toastTimeout);
    elements.toast.textContent = message;
    elements.toast.className = `toast visible ${kind}`;
    toastTimeout = window.setTimeout(() => elements.toast.classList.remove("visible"), 3900);
}
