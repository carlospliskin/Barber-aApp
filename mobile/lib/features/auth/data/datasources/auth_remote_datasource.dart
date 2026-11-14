import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

/// Responsabilidad única: ejecutar llamadas HTTP al backend GraphQL.
/// Retorna Map<String,dynamic> crudo o lanza Exception.
/// No conoce nada de dominio, estado ni navegación.
class AuthRemoteDatasource {
  static const _baseUrl = kDebugMode
      ? 'http://10.0.2.2:8080/graphql'
      : 'https://api.barberia.app/graphql';

  static const _authFields = '''
    token refreshToken userId capabilities primaryRole
    tenant { id name timezone plan }
    clientProfile { id name phone isPenalized penaltyUntil noShowCount }
  ''';

  final http.Client _client;

  AuthRemoteDatasource({http.Client? client})
      : _client = client ?? http.Client();

  Future<Map<String, dynamic>> login({
    required String email,
    required String password,
  }) async {
    final data = await _mutate(
      '''mutation(\$e:String!,\$p:String!){login(email:\$e,password:\$p){$_authFields}}''',
      {'e': email, 'p': password},
    );
    return data['login'] as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> registerOwner({
    required String email,
    required String password,
    required String barberiaName,
    required String ownerName,
    String? phone,
  }) async {
    final data = await _mutate(
      '''mutation(\$e:String!,\$p:String!,\$b:String!,\$o:String!,\$ph:String){
        register(email:\$e,password:\$p,barberiaName:\$b,ownerName:\$o,phone:\$ph){$_authFields}}''',
      {'e': email, 'p': password, 'b': barberiaName, 'o': ownerName, 'ph': phone},
    );
    return data['register'] as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> registerClient({
    required String email,
    required String password,
    required String name,
    required String phone,
  }) async {
    final data = await _mutate(
      '''mutation(\$e:String!,\$p:String!,\$n:String!,\$ph:String!){
        registerClient(email:\$e,password:\$p,name:\$n,phone:\$ph){$_authFields}}''',
      {'e': email, 'p': password, 'n': name, 'ph': phone},
    );
    return data['registerClient'] as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> switchMode({
    required String mode,
    required String token,
  }) async {
    final data = await _mutate(
      '''mutation(\$m:String!){switchMode(mode:\$m){$_authFields}}''',
      {'m': mode},
      token: token,
    );
    return data['switchMode'] as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> me({required String token}) async {
    final data = await _query(
      '''{me{userId email name capabilities primaryRole
        tenant{id name timezone plan}
        clientProfile{id name phone isPenalized penaltyUntil noShowCount}}}''',
      token: token,
    );
    return data['me'] as Map<String, dynamic>;
  }

  // ─── HTTP internos ─────────────────────────────────────────────────────────

  Future<Map<String, dynamic>> _query(String q, {String? token}) =>
      _send({'query': q}, token: token);

  Future<Map<String, dynamic>> _mutate(
    String mutation,
    Map<String, dynamic?> vars, {
    String? token,
  }) =>
      _send({'query': mutation, 'variables': vars..removeWhere((_, v) => v == null)}, token: token);

  Future<Map<String, dynamic>> _send(
    Map<String, dynamic> body, {
    String? token,
  }) async {
    try {
      final response = await _client
          .post(
            Uri.parse(_baseUrl),
            headers: {
              'Content-Type': 'application/json',
              if (token != null) 'Authorization': 'Bearer $token',
            },
            body: jsonEncode(body),
          )
          .timeout(const Duration(seconds: 15));

      if (response.statusCode != 200) {
        throw _HttpException(response.statusCode);
      }

      final json = jsonDecode(response.body) as Map<String, dynamic>;
      if (json['errors'] != null) {
        final msg = (json['errors'] as List).first['message'] as String;
        throw _GraphQLException(msg);
      }

      return json['data'] as Map<String, dynamic>;
    } on SocketException {
      rethrow; // El repository lo convierte en AuthFailure.noConnection
    } on _HttpException {
      rethrow;
    } on _GraphQLException {
      rethrow;
    } catch (e) {
      throw _GraphQLException(e.toString());
    }
  }
}

class _HttpException implements Exception {
  final int code;
  _HttpException(this.code);
  @override String toString() => 'HTTP Error $code';
}

class _GraphQLException implements Exception {
  final String message;
  _GraphQLException(this.message);
  @override String toString() => message;
}
