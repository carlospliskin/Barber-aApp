import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

class LoyaltyRemoteDatasource {
  static const _baseUrl = kDebugMode
      ? 'http://10.0.2.2:8080/graphql'
      : 'https://api.barberia.app/graphql';

  final http.Client _client;
  LoyaltyRemoteDatasource({http.Client? client})
      : _client = client ?? http.Client();

  // ─── Estado de apertura ───────────────────────────────────────────────────

  Future<Map<String, dynamic>> getTenantStatus({
    required String tenantId,
    required String token,
  }) async {
    final data = await _query(
      '''query TenantStatus(\$id: String!) {
        tenantStatus(tenantId: \$id) {
          isOpen source opensAt closesAt
        }
      }''',
      variables: {'id': tenantId},
      token: token,
    );
    return data['tenantStatus'] as Map<String, dynamic>? ?? {'isOpen': false, 'source': 'NO_SCHEDULE'};
  }

  Future<Map<String, dynamic>> setTenantStatus({
    required String tenantId,
    required bool isOpen,
    required String token,
    String? reason,
  }) async {
    final data = await _mutate(
      '''mutation SetStatus(\$tenantId: String!, \$isOpen: Boolean!, \$reason: String) {
        setTenantOpenStatus(tenantId: \$tenantId, isOpen: \$isOpen, reason: \$reason) {
          isOpen source opensAt closesAt
        }
      }''',
      variables: {'tenantId': tenantId, 'isOpen': isOpen, 'reason': reason},
      token: token,
    );
    return data['setTenantOpenStatus'] as Map<String, dynamic>;
  }

  // ─── Programa de fidelización ─────────────────────────────────────────────

  Future<Map<String, dynamic>?> getLoyaltyProgram({
    required String tenantId,
    required String token,
  }) async {
    final data = await _query(
      '''query LoyaltyProgram {
        loyaltyProgram {
          id tenantId name description isActive totalMembers
          rules {
            id programId name description type
            triggerValue rewardType rewardValue isActive sortOrder
          }
        }
      }''',
      token: token,
    );
    return data['loyaltyProgram'] as Map<String, dynamic>?;
  }

  Future<Map<String, dynamic>> upsertLoyaltyProgram({
    required String name,
    String? description,
    required String token,
  }) async {
    final data = await _mutate(
      '''mutation UpsertProgram(\$input: UpsertLoyaltyProgramInput!) {
        upsertLoyaltyProgram(input: \$input) {
          id tenantId name description isActive totalMembers
          rules {
            id programId name description type
            triggerValue rewardType rewardValue isActive sortOrder
          }
        }
      }''',
      variables: {
        'input': {
          'name': name,
          if (description != null) 'description': description,
        },
      },
      token: token,
    );
    return data['upsertLoyaltyProgram'] as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> addLoyaltyRule({
    required String programId,
    required Map<String, dynamic> ruleInput,
    required String token,
  }) async {
    final data = await _mutate(
      '''mutation AddRule(\$programId: String!, \$input: LoyaltyRuleInput!) {
        addLoyaltyRule(programId: \$programId, input: \$input) {
          id name description type triggerValue rewardType rewardValue isActive
        }
      }''',
      variables: {'programId': programId, 'input': ruleInput},
      token: token,
    );
    return data['addLoyaltyRule'] as Map<String, dynamic>;
  }

  Future<void> toggleLoyaltyRule({
    required String ruleId,
    required bool isActive,
    required String token,
  }) async {
    await _mutate(
      '''mutation ToggleRule(\$id: String!, \$active: Boolean!) {
        toggleLoyaltyRule(ruleId: \$id, isActive: \$active)
      }''',
      variables: {'id': ruleId, 'active': isActive},
      token: token,
    );
  }

  // ─── Servicios del owner ──────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getServices({required String token}) async {
    final data = await _query(
      '''query {
        services {
          id name category durationMinutes basePrice
          depositType depositAmount maxConcurrent isActive
        }
      }''',
      token: token,
    );
    return (data['services'] as List? ?? []).cast<Map<String, dynamic>>();
  }

  // ─── Barberos con horario de hoy ──────────────────────────────────────────

  Future<List<Map<String, dynamic>>> getBarbersWithSchedule({
    required String token,
  }) async {
    final today = DateTime.now().weekday - 1; // 0=Lunes
    final data = await _query(
      '''query BarbersSchedule(\$day: Int!) {
        barbers {
          id name
          schedule(dayOfWeek: \$day) {
            isWorking openTime closeTime capacity
          }
        }
      }''',
      variables: {'day': today},
      token: token,
    );
    return (data['barbers'] as List? ?? []).cast<Map<String, dynamic>>();
  }

  // ─── HTTP helpers ─────────────────────────────────────────────────────────

  Future<Map<String, dynamic>> _query(
    String q, {
    Map<String, dynamic>? variables,
    required String token,
  }) => _send({'query': q, if (variables != null) 'variables': variables}, token: token);

  Future<Map<String, dynamic>> _mutate(
    String m, {
    Map<String, dynamic>? variables,
    required String token,
  }) => _send({'query': m, if (variables != null) 'variables': variables..removeWhere((_, v) => v == null)}, token: token);

  Future<Map<String, dynamic>> _send(
    Map<String, dynamic> body, {
    required String token,
  }) async {
    final response = await _client
        .post(
          Uri.parse(_baseUrl),
          headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer $token',
          },
          body: jsonEncode(body),
        )
        .timeout(const Duration(seconds: 15));

    if (response.statusCode == 401) throw _UnauthorizedException();
    if (response.statusCode != 200) throw _HttpException(response.statusCode);

    final json = jsonDecode(response.body) as Map<String, dynamic>;
    if (json['errors'] != null) {
      final msg = (json['errors'] as List).first['message'] as String;
      throw _GraphQLException(msg);
    }
    return json['data'] as Map<String, dynamic>;
  }
}

class _HttpException implements Exception {
  final int code;
  _HttpException(this.code);
}
class _UnauthorizedException implements Exception {}
class _GraphQLException implements Exception {
  final String message;
  _GraphQLException(this.message);
  @override String toString() => message;
}
