package br.com.fiap.velha.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

import br.com.fiap.velha.handler.HandlerDoJogoDaVelha;

public class JogoDaVelhaServer {

	private static final int PORTA_TCP = 9123;
	private static final int TAMANHO_BUFFER_DO_ACCEPTOR = 2048;

	public static void main(String[] args) throws IOException {

		IoAcceptor ioAcceptor = new NioSocketAcceptor();

		ioAcceptor.getFilterChain().addLast("loggingFilter", new LoggingFilter());
		ioAcceptor.getFilterChain().addLast("protocolCodecFilter", new ProtocolCodecFilter(new TextLineCodecFactory(Charset.forName("UTF-8"))));

		ioAcceptor.setHandler(new HandlerDoJogoDaVelha());
		ioAcceptor.getSessionConfig().setReadBufferSize(TAMANHO_BUFFER_DO_ACCEPTOR);

		ioAcceptor.bind(new InetSocketAddress(PORTA_TCP));

		if (HandlerDoJogoDaVelha.DEBUG_ATIVO) {
			System.out.println("Bem vindo ao Jogo da Velha Server!");
			System.out.println("Servidor Pronto para receber conexões!");
			System.out.println("Aguardando as conexões...");
		}
	}

}
