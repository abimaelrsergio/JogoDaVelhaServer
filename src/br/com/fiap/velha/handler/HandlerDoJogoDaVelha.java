package br.com.fiap.velha.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;

import br.com.fiap.velha.engine.MotorDoJogoDaVelha;
import br.com.fiap.velha.parser.VelhaParser;
import br.com.fiap.velha.pojo.Velha;

public class HandlerDoJogoDaVelha extends IoHandlerAdapter {

	public static final boolean DEBUG_ATIVO = true;

	private class Cliente {

		private IoSession sessao = null;
		private char id = MotorDoJogoDaVelha.JOGADOR_VAZIO;

		public void setSessao(IoSession sessao) {
			this.sessao = sessao;
		}

		public IoSession getSessao() {
			return sessao;
		}

		public void setId(char id) {
			this.id = id;
		}

		public char getId() {
			return id;
		}
	}

	private List<Cliente> clientes = new ArrayList<Cliente>();

	/**
	 * Indice na lista de clientes, correspondente ao jogador da vez -1, 0 ou 1
	 */
	private int jogadorDaVez = -1;

	private MotorDoJogoDaVelha motor = new MotorDoJogoDaVelha();
	private VelhaParser velhaParser = new VelhaParser();
	/** Bean (POJO) com a representacao do XML do Jogo da Velha. */
	private Velha velhaBean = new Velha();

	/**
	 * Evento disparado quando uma sessao (conexao) e' aberta.
	 * 
	 * @param session
	 *            Referencia para objeto que representa a sessao
	 */
	@Override
	public void sessionOpened(IoSession session) {

		int clientsSizeBefore = clientes.size();
		int clientsSizeAfter = clientsSizeBefore;

		Cliente client = null;

		if (clientsSizeBefore == 2) {
			/* entrou um terceiro jogador, ele sera' derrubado */

			if (DEBUG_ATIVO) {
				System.out.println("Conexao com " + session.getRemoteAddress().toString() + " finalizada.");
			}

			session.close(false);
			return;

		} else { /* entrou algum jogador */

			/* guarda a referencia da sessao */
			client = new Cliente();
			client.setSessao(session);

			/* sorteia um Id de jogador (X ou O) */
			char idJogador = (new Random().nextInt(2) == 0) ? MotorDoJogoDaVelha.JOGADOR_X : MotorDoJogoDaVelha.JOGADOR_O;

			/* configura o Id do jogador */
			switch (clientsSizeBefore) {
			case 0:
				client.setId(idJogador);
				break;
			case 1:
				client.setId(MotorDoJogoDaVelha.getOponente(clientes.get(0).getId()));
				break;
			}
			/* adiciona cliente 'a lista */
			clientes.add(client);
			clientsSizeAfter = clientes.size();

			if (DEBUG_ATIVO) {
				System.out.println("Jogador " + String.valueOf(client.getId()) + " conectou ("
						+ getClientAddress(client) + ").");
			}

			/* envia um XML ao cliente, com status AGUARDE */
			enviarStatusJogo(client, MotorDoJogoDaVelha.STATUS_AGUARDE);

		}

		if (clientsSizeBefore == 1 && clientsSizeAfter == 2) { /*
																 * entrou o
																 * segundo
																 * jogador
																 */

			/* sorteia quem comeca o jogo */
			jogadorDaVez = new Random().nextInt(2);
			client = clientes.get(jogadorDaVez);

			if (DEBUG_ATIVO) {
				System.out.println("Jogador " + String.valueOf(client.getId()) + " inicia o jogo.");
			}

			/* envia um XML ao cliente, com status JOGUE */
			enviarStatusJogo(client, MotorDoJogoDaVelha.STATUS_JOGUE);
		}
	}

	/**
	 * Evento disparado quando uma sessao (conexao) e' fechada.
	 * 
	 * @param session
	 *            Referencia para objeto que representa a sessao
	 */
	@Override
	public void sessionClosed(IoSession session) {

		int clientsSizeBefore = clientes.size();
		int clientsSizeAfter = clientsSizeBefore;

		Cliente client = null;

		/* algum cliente desconectou */
		int idx = getIdxBySessao(session);

		if (idx != -1) {

			client = clientes.get(idx);

			if (DEBUG_ATIVO) {
				System.out.println("Jogador " + String.valueOf(client.getId()) + " desconectou ("
						+ getClientAddress(client) + ").");
			}
			/* remove da lista */
			clientes.remove(client);
			clientsSizeAfter = clientes.size();
		}

		/* um jogador saiu mas ficou o outro */
		if (clientsSizeBefore == 2 && clientsSizeAfter == 1) {

			client = clientes.get(0);
			/* envia um XML ao cliente, com status WO */
			enviarStatusJogo(client, MotorDoJogoDaVelha.STATUS_WO);
			/* fecha a conexao */
			client.getSessao().close(true);

		} else if (clientsSizeBefore == 1 && clientsSizeAfter == 0) {
			/* o ultimo jogador saiu, nao tem mais ninguem pra jogar */
			/* o servidor fica ocioso esperando alguem conectar */
			if (DEBUG_ATIVO)
				System.out.println("Aguardando conexoes...");
		}

		/* se um dos jogadores desconectou, reinicia status do jogo */
		if (clientsSizeAfter < 2) {
			jogadorDaVez = -1;
			motor.limparTabuleiro();
			velhaBean.limpar();
		}
	}

	/**
	 * Evento disparado quando uma mensagem e' recebida.
	 * 
	 * @param session
	 *            Referencia para objeto que representa a sessao
	 * @param message
	 *            Mensagem recebida
	 */
	@Override
	public void messageReceived(IoSession session, Object message) {
		/* processa a msg recebida */
		processarMensagem(session, message.toString());
	}

	/**
	 * Processa uma mensagem recebida, realizando as acoes correspondentes.
	 * 
	 * @param session
	 *            Sessao do cliente que enviou a mensagem
	 * @param message
	 *            Mensagem em formato texto
	 */
	private void processarMensagem(IoSession session, String message) {

		/* se nao tem 2 jogadores conectados, ignora mensagem */
		if (clientes.size() != 2) {
			return;
		}
		/* se e' a vez de nenhum jogador, ignora mensagem */
		if (jogadorDaVez != 0 && jogadorDaVez != 1) {
			return;
		}

		Cliente client = null;

		/* procura na lista de conexoes */
		int idx = getIdxBySessao(session);

		/* se nao esta' na lista, ignora mensagem */
		if (idx == -1) {
			return;
		}

		if (DEBUG_ATIVO) {
			client = clientes.get(idx);
			System.out.println("  Jogador " + String.valueOf(client.getId()) + " ("
					+ getClientAddress(client) + "):");
			System.out.println("    <= XML recebido: " + message.toString());
		}

		/* verifica se jogador esta' na vez certa */
		if (jogadorDaVez == idx) {
			/* ok, jogador na vez certa */

			/* converte o XML da mensagem para um objeto VelhaBean */
			Velha tempBean = velhaParser.getVelhaBean(message);

			/* verifica objeto VelhaBean gerado a partir do XML */
			if (verificarVelhaBean(tempBean)) {
				/* se nao houve erros, executa as acoes */
				executarAcaoJogada(tempBean);

			} else {
				/* se houve erro, reenvia o XML ao cliente */
				client = clientes.get(jogadorDaVez);

				if (DEBUG_ATIVO) {
					System.out.println("Jogador " + String.valueOf(client.getId())
							+ " enviou um XML com problemas.");
				}

				/* envia um XML ao cliente, com status JOGUE */
				enviarStatusJogo(client, MotorDoJogoDaVelha.STATUS_JOGUE);
			}

		} else {
			/* errado! nao e' a vez desse jogador */
			/* reenvia o XML ao cliente */
			client = clientes.get(idx);

			if (DEBUG_ATIVO) {
				System.out.println("Jogador " + String.valueOf(client.getId()) + " jogou na vez errada!");
			}

			/* envia um XML ao cliente, com status AGUARDE */
			enviarStatusJogo(client, MotorDoJogoDaVelha.STATUS_AGUARDE);
		}
	}

	/**
	 * Envia um XML ao cliente, com o status especificado.
	 * 
	 * @param client
	 *            Conexao do cliente
	 * @param status
	 *            Status a ser preenchido no XML
	 */
	private void enviarStatusJogo(Cliente client, String status) {

		/* configura o bean que representa o XML */
		velhaBean.setId(client.getId());
		velhaBean.setStatus(status);
		velhaBean.setJogada(-1);
		velhaBean.copiarTabuleiro(motor.getTabuleiro());

		/* envia um XML ao cliente (jogador da vez) */
		String velhaXML = velhaParser.getVelhaXML(velhaBean);
		if (client.sessao.isConnected()) {

			client.sessao.write(velhaXML);

			if (DEBUG_ATIVO) {
				System.out.println("  Jogador " + String.valueOf(client.getId()) + "("
						+ getClientAddress(client) + "):");
				System.out.println("    => XML enviado: " + velhaXML);
			}
		}

	}

	/**
	 * Executa as acoes da jogada.
	 * 
	 * @param velha
	 *            Objeto VelhaBean
	 */
	private void executarAcaoJogada(Velha velha) {

		/* calcula indice do proximo jogador na lista de conexoes */
		int jogadorProximo = -1;
		if (jogadorDaVez == 0)
			jogadorProximo = 1;
		else if (jogadorDaVez == 1)
			jogadorProximo = 0;

		/* conexoes dos jogadores */
		Cliente clientVez = clientes.get(jogadorDaVez);
		Cliente clientProx = clientes.get(jogadorProximo);

		/* status dos jogadores */
		String statusVez = MotorDoJogoDaVelha.STATUS_AGUARDE;
		String statusProx = MotorDoJogoDaVelha.STATUS_JOGUE;

		/* registra jogada */
		motor.setPosicao(velha.getJogada(), velha.getId());

		if (DEBUG_ATIVO) {
			System.out.println("Jogador " + String.valueOf(clientVez.getId()) + " jogou na posicao "
					+ velha.getJogada());
		}

		/* verifica se houve empate */
		if (motor.isEmpate()) {
			/* status dos jogadores */
			statusVez = MotorDoJogoDaVelha.STATUS_EMPATE;
			statusProx = MotorDoJogoDaVelha.STATUS_EMPATE;

			if (DEBUG_ATIVO)
				System.out.println("Houve empate!");

			/* verifica se jogador da vez ganhou */
		} else if (motor.isGanhador(clientVez.getId())) {
			/* status dos jogadores */
			statusVez = MotorDoJogoDaVelha.STATUS_GANHOU;
			statusProx = MotorDoJogoDaVelha.STATUS_PERDEU;

			if (DEBUG_ATIVO) {
				System.out.println("Jogador " + String.valueOf(clientVez.getId()) + " ganhou!");
			}
		}

		/* configura proximo jogador */
		jogadorDaVez = jogadorProximo;

		/* envia um XML ao cliente (jogador da vez) */
		enviarStatusJogo(clientVez, statusVez);
		/* envia um XML ao cliente (proximo jogador) */
		enviarStatusJogo(clientProx, statusProx);

		/* verifica se jogo encerrou (game over) */
		if (motor.isGameOver()) {
			/* fecha conexoes dos clientes */
			clientVez.getSessao().close(true);
			clientProx.getSessao().close(true);

			if (DEBUG_ATIVO)
				System.out.println("Game over!");
		}
	}

	/**
	 * Retorna o indice na lista de clientes conectados, a partir de um objeto
	 * sessao.
	 * 
	 * @param session
	 *            Sessao do cliente conectado
	 * @return Indice do cliente na lista, ou -1 se sessao nao esta' na lista.
	 */
	private int getIdxBySessao(IoSession session) {
		/* pesquisa na lista de clientes conectados */
		for (int i = 0; i < clientes.size(); i++) {
			if (clientes.get(i).sessao == session) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Verifica se um objeto VelhaBean e' valido.
	 * 
	 * @param velha
	 *            Objeto VelhaBean
	 * @return True se objeto e' valido, false se nao.
	 */
	private boolean verificarVelhaBean(Velha velha) {

		/* problema de conversao do XML */
		if (velha == null)
			return false;

		/* verifica se Id do jogador esta' certo no XML */
		if (velha.getId() != clientes.get(jogadorDaVez).getId()) {
			return false;
		}

		/* verifica se jogada e' valida */
		int jogada = velha.getJogada();
		if (jogada < 0 || motor.getPosicao(jogada) != MotorDoJogoDaVelha.JOGADOR_VAZIO) {
			return false;
		}
		return true;
	}

	/**
	 * Retorna o endereco IP do cliente remoto.
	 * 
	 * @param client
	 *            Conexao do cliente
	 * @return Endereco IP do cliente ou vazio se houve errro.
	 */
	private String getClientAddress(Cliente client) {
		String address = "";
		if (client != null && client.getSessao() != null && client.getSessao().getRemoteAddress() != null) {
			address = client.getSessao().getRemoteAddress().toString();
		}
		return address;
	}

	/**
	 * Em caso de excecao, este metodo e' disparado.
	 * 
	 * @param session
	 *            Referencia para objeto que representa a sessao
	 * @param cause
	 *            Excecao ocorrida
	 */
	@Override
	public void exceptionCaught(IoSession session, Throwable cause) {

		if (DEBUG_ATIVO)
			cause.printStackTrace();
		session.close(true);
	}
}
